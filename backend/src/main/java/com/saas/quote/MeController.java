package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

/** What the front end needs to build menus: who I am and exactly what I may do. */
@RestController @RequestMapping("/api/me") @RequiredArgsConstructor @Transactional(readOnly = true)
public class MeController {
    private final UserRepo users;

    record Me(Long id, String name, String email, String companyName, String portal, boolean mustChangePassword,
              boolean seesAllQuotations, Set<String> permissions) {}

    @GetMapping
    public Me me(@AuthenticationPrincipal AuthUser me) {
        AppUser u = users.findById(me.userId()).orElseThrow();
        String company = users.findById(me.tenantId()).map(AppUser::getCompanyName).orElse("");
        return new Me(u.getId(), me.name(), u.getEmail(), company, me.admin() ? "ADMIN" : "EMPLOYEE", me.mustChangePassword(),
                me.seesAllQuotations(), me.perms());
    }
}
