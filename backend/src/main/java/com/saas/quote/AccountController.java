package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController @RequestMapping("/api/account") @RequiredArgsConstructor @Transactional
public class AccountController {
    private final UserRepo users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    record EmailChange(String newEmail, String currentPassword) {}
    record PasswordChange(String currentPassword, String newPassword) {}

    @GetMapping
    public Map<String, String> get(@AuthenticationPrincipal AuthUser me) {
        return Map.of("email", users.findById(me.userId()).orElseThrow().getEmail());
    }

    /** Sign-in email is managed by administrators for employees. */
    @PutMapping("/email")
    public Map<String, String> changeEmail(@AuthenticationPrincipal AuthUser me, @RequestBody EmailChange r) {
        if (!me.admin()) throw bad(HttpStatus.FORBIDDEN, "Ask your administrator to change your email address");
        AppUser u = verified(me.userId(), r.currentPassword());
        String e = r.newEmail() == null ? "" : r.newEmail().trim().toLowerCase();
        if (!e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw bad(HttpStatus.BAD_REQUEST, "Enter a valid email address");
        if (!e.equals(u.getEmail()) && users.existsByEmail(e)) throw bad(HttpStatus.CONFLICT, "That email is already used by another account");
        u.setEmail(e);
        users.save(u);
        return Map.of("email", e);
    }

    /** Also used for the first sign-in: the temporary password is the "current" password. */
    @PutMapping("/password")
    public void changePassword(@AuthenticationPrincipal AuthUser me, @RequestBody PasswordChange r) {
        AppUser u = verified(me.userId(), r.currentPassword());
        if (r.newPassword() == null || r.newPassword().length() < 8) throw bad(HttpStatus.BAD_REQUEST, "New password must be at least 8 characters");
        if (r.newPassword().equals(r.currentPassword())) throw bad(HttpStatus.BAD_REQUEST, "Choose a password different from the current one");
        u.setPassword(encoder.encode(r.newPassword()));
        u.setMustChangePassword(false);
        users.save(u);
        audit.log(me, AppModule.USERS, "PASSWORD_CHANGE", "User", u.getId(), "Changed own password");
    }

    private AppUser verified(Long uid, String currentPassword) {
        AppUser u = users.findById(uid).orElseThrow();
        if (currentPassword == null || !encoder.matches(currentPassword, u.getPassword()))
            throw bad(HttpStatus.FORBIDDEN, "Current password is wrong");   // 403, not 401, so the app does not sign you out
        return u;
    }

    private ResponseStatusException bad(HttpStatus s, String msg) { return new ResponseStatusException(s, msg); }
}
