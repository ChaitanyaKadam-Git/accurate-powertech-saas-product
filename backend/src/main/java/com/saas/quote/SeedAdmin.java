package com.saas.quote;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/**
 * Creates the first company and its Admin at start-up when SEED_ADMIN_EMAIL and SEED_ADMIN_PASSWORD are set, the same
 * way "Create a new company account" does. Does nothing when the email already exists, so restarts are safe. The Admin
 * must replace the seed password at first sign-in.
 */
@Slf4j @Component @RequiredArgsConstructor
public class SeedAdmin implements ApplicationRunner {
    private final UserRepo users;
    private final SectionRepo sections;
    private final AccessService access;
    private final PasswordEncoder encoder;

    @Value("${app.seed.admin-email:}") private String email;
    @Value("${app.seed.admin-password:}") private String password;
    @Value("${app.seed.admin-name:}") private String name;
    @Value("${app.seed.company-name:Accurate Powertech}") private String companyName;

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) return;
        String mail = email.trim().toLowerCase();
        if (users.existsByEmail(mail)) { log.info("Seed admin {} already exists, nothing to do", mail); return; }
        if (password.length() < 8) { log.warn("SEED_ADMIN_PASSWORD has fewer than 8 characters; seed admin not created"); return; }

        AppUser u = new AppUser();
        u.setEmail(mail);
        u.setPassword(encoder.encode(password));
        u.setCompanyName(companyName.trim());
        u.setName(name.isBlank() ? companyName.trim() : name.trim());
        u.setActive(true); u.setMustChangePassword(true); u.setCreatedAt(Instant.now());
        users.save(u);
        u.setTenantId(u.getId());
        u.setRole(access.seedRoles(u.getId()));
        sections.saveAll(DefaultSections.forOwner(u.getId()));
        log.info("Seed admin {} created for company {}", mail, u.getCompanyName());
    }
}
