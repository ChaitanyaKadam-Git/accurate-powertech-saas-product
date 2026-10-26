package com.saas.quote;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final UserRepo users;
    private final SectionRepo sections;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    record Register(@Email @NotBlank String email, @Size(min = 8) String password, @NotBlank String companyName) {}
    record Login(String email, String password) {}
    record Token(String token, String companyName) {}

    @PostMapping("/register") @Transactional
    public Token register(@Valid @RequestBody Register r) {
        if (users.existsByEmail(r.email())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        AppUser u = new AppUser();
        u.setEmail(r.email().toLowerCase());
        u.setPassword(encoder.encode(r.password()));
        u.setCompanyName(r.companyName());
        users.save(u);
        sections.saveAll(DefaultSections.forOwner(u.getId()));   // every new tenant starts with the standard text
        return new Token(jwt.create(u.getId(), u.getEmail()), u.getCompanyName());
    }

    @PostMapping("/login")
    public Token login(@RequestBody Login l) {
        AppUser u = users.findByEmail(l.email() == null ? "" : l.email().toLowerCase())
                .filter(x -> encoder.matches(l.password(), x.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return new Token(jwt.create(u.getId(), u.getEmail()), u.getCompanyName());
    }
}
