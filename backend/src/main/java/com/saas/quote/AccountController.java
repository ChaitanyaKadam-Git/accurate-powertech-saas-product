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

    record EmailChange(String newEmail, String currentPassword) {}
    record PasswordChange(String currentPassword, String newPassword) {}

    @GetMapping
    public Map<String, String> get(@AuthenticationPrincipal Long uid) {
        return Map.of("email", users.findById(uid).orElseThrow().getEmail());
    }

    @PutMapping("/email")
    public Map<String, String> changeEmail(@AuthenticationPrincipal Long uid, @RequestBody EmailChange r) {
        AppUser u = verified(uid, r.currentPassword());
        String e = r.newEmail() == null ? "" : r.newEmail().trim().toLowerCase();
        if (!e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw bad(HttpStatus.BAD_REQUEST, "Enter a valid email address");
        if (!e.equals(u.getEmail()) && users.existsByEmail(e)) throw bad(HttpStatus.CONFLICT, "That email is already used by another account");
        u.setEmail(e);
        users.save(u);
        return Map.of("email", e);
    }

    @PutMapping("/password")
    public void changePassword(@AuthenticationPrincipal Long uid, @RequestBody PasswordChange r) {
        AppUser u = verified(uid, r.currentPassword());
        if (r.newPassword() == null || r.newPassword().length() < 8) throw bad(HttpStatus.BAD_REQUEST, "New password must be at least 8 characters");
        u.setPassword(encoder.encode(r.newPassword()));
        users.save(u);
    }

    private AppUser verified(Long uid, String currentPassword) {
        AppUser u = users.findById(uid).orElseThrow();
        if (currentPassword == null || !encoder.matches(currentPassword, u.getPassword()))
            throw bad(HttpStatus.FORBIDDEN, "Current password is wrong");   // 403, not 401, so the app does not sign you out
        return u;
    }

    private ResponseStatusException bad(HttpStatus s, String msg) { return new ResponseStatusException(s, msg); }
}
