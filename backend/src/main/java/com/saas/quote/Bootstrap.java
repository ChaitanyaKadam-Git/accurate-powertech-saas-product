package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/** Upgrades accounts created before roles existed: each old account becomes a company with itself as Admin. */
@Component @RequiredArgsConstructor
public class Bootstrap implements ApplicationRunner {
    private final UserRepo users;
    private final AccessService access;

    @Override @Transactional
    public void run(ApplicationArguments args) {
        for (AppUser u : users.findByTenantIdIsNull()) {
            u.setTenantId(u.getId());
            u.setRole(access.seedRoles(u.getId()));
            u.setActive(true);
            u.setMustChangePassword(false);
            if (u.getName() == null) u.setName(u.getCompanyName());
            if (u.getCreatedAt() == null) u.setCreatedAt(Instant.now());
            users.save(u);
        }
    }
}
