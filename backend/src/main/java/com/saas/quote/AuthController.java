package com.saas.quote;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Set;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final UserRepo users;
    private final SectionRepo sections;
    private final SessionRepo sessions;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AccessService access;
    private final AuditService audit;

    record Register(@Email @NotBlank String email, @Size(min = 8) String password, @NotBlank String companyName, String name) {}
    record Login(String email, String password, String portal) {}
    record Signed(String token, String companyName, String name, String portal, boolean mustChangePassword, Set<String> permissions) {}

    /** Creates a new company and its first administrator. */
    @PostMapping("/register") @Transactional
    public Signed register(@Valid @RequestBody Register r) {
        if (users.existsByEmail(r.email().toLowerCase())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        AppUser u = new AppUser();
        u.setEmail(r.email().toLowerCase());
        u.setPassword(encoder.encode(r.password()));
        u.setCompanyName(r.companyName());
        u.setName(r.name() == null || r.name().isBlank() ? r.companyName() : r.name().trim());
        u.setActive(true); u.setMustChangePassword(false); u.setCreatedAt(Instant.now());
        users.save(u);
        u.setTenantId(u.getId());
        u.setRole(access.seedRoles(u.getId()));
        sections.saveAll(DefaultSections.forOwner(u.getId()));   // every new company starts with the standard text
        return sign(u, "ADMIN");
    }

    /** One login form for everyone: the account's role decides whether it opens the admin console or the employee workspace. */
    @PostMapping("/login") @Transactional
    public Signed login(@RequestBody Login l) {
        AppUser u = users.findByEmail(l.email() == null ? "" : l.email().trim().toLowerCase()).orElse(null);
        if (u == null || l.password() == null || !encoder.matches(l.password(), u.getPassword())) {
            if (u != null) audit.logAuth(u.getTenantId(), u.getId(), u.displayName(), "LOGIN_FAILED", "wrong password");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (!u.enabled()) {
            audit.logAuth(u.getTenantId(), u.getId(), u.displayName(), "LOGIN_FAILED", "account deactivated");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account is deactivated. Please contact your administrator.");
        }
        return sign(u, u.isAdminUser() ? "ADMIN" : "EMPLOYEE");
    }

    @PostMapping("/logout") @Transactional
    public void logout(@AuthenticationPrincipal AuthUser me) {
        sessions.findById(me.sessionId()).ifPresent(s -> { if (s.getSignedOutAt() == null) { s.setSignedOutAt(Instant.now()); s.setSignOutReason("SIGN_OUT"); } });
    }

    private Signed sign(AppUser u, String portal) {
        u.setLastLoginAt(Instant.now());
        users.save(u);
        LoginSession s = new LoginSession();
        s.setTenantId(u.getTenantId()); s.setUserId(u.getId()); s.setUserName(u.displayName()); s.setUserEmail(u.getEmail());
        s.setPortal(portal); s.setSignedInAt(Instant.now()); s.setIp(ClientIp.current()); s.setUserAgent(ClientIp.agent());
        sessions.save(s);
        AuthUser me = access.principal(u, s.getId());
        String company = users.findById(me.tenantId()).map(AppUser::getCompanyName).orElse("");
        return new Signed(jwt.create(u.getId(), s.getId(), portal), company, u.displayName(), portal, me.mustChangePassword(), me.perms());
    }
}
