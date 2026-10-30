package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** Works out what a person may do: role rights + per-user grants - per-user revokes. */
@Service @RequiredArgsConstructor
public class AccessService {
    private final RoleRepo roles;

    public static Set<String> allKeys() {
        Set<String> s = new LinkedHashSet<>();
        for (AppModule m : AppModule.values()) for (Right r : m.rights) s.add(AppModule.key(m, r));
        return s;
    }

    /** Rights the role itself gives (admin role: everything). */
    public Set<String> roleBase(Role r) {
        if (r == null) return new HashSet<>();
        if (r.isAdminRole()) return allKeys();
        Set<String> out = new HashSet<>(r.getRights());
        out.removeIf(k -> !AppModule.valid(k) || AppModule.isAdminOnly(k));
        return out;
    }

    public Set<String> effective(AppUser u) {
        if (u.isAdminUser()) return allKeys();
        Set<String> out = roleBase(u.getRole());
        out.addAll(u.getGrants());
        out.removeAll(u.getRevokes());
        out.removeIf(k -> !AppModule.valid(k) || AppModule.isAdminOnly(k));
        return out;
    }

    public AuthUser principal(AppUser u, Long sessionId) {
        boolean all = u.isAdminUser() || (u.getRole() != null && "ALL".equals(u.getRole().getQuotationScope()));
        return new AuthUser(u.getId(), u.getTenantId() == null ? u.getId() : u.getTenantId(), u.displayName(),
                u.isAdminUser(), u.mustChange(), effective(u), all, sessionId);
    }

    /** Creates Admin, Manager and Sales Executive for a company (only those that do not exist yet). Returns the Admin role. */
    public Role seedRoles(Long tenantId) {
        Role admin = roles.findByTenantIdAndNameIgnoreCase(tenantId, "Admin").orElseGet(() -> {
            Role r = new Role();
            r.setTenantId(tenantId); r.setName("Admin"); r.setDescription("Full access, user and rights administration");
            r.setSystemRole(true); r.setAdminRole(true); r.setQuotationScope("ALL");
            return roles.save(r);
        });
        if (roles.findByTenantIdAndNameIgnoreCase(tenantId, "Manager").isEmpty()) {
            Role r = new Role();
            r.setTenantId(tenantId); r.setName("Manager"); r.setDescription("Works on every quotation and master record");
            r.setQuotationScope("ALL");
            for (AppModule m : AppModule.values()) if (!m.adminOnly) for (Right x : m.rights) r.getRights().add(AppModule.key(m, x));
            roles.save(r);
        }
        if (roles.findByTenantIdAndNameIgnoreCase(tenantId, "Sales Executive").isEmpty()) {
            Role r = new Role();
            r.setTenantId(tenantId); r.setName("Sales Executive"); r.setDescription("Creates quotations and sees only their own");
            r.setQuotationScope("OWN");
            for (String k : List.of("ENQUIRIES:VIEW", "ENQUIRIES:CREATE", "ENQUIRIES:EDIT", "CUSTOMERS:VIEW", "CUSTOMERS:CREATE", "CUSTOMERS:EDIT", "PRODUCTS:VIEW", "PARTS:VIEW", "TEMPLATES:VIEW", "TEMPLATE_DATA:VIEW",
                    "QUOTATIONS:VIEW", "QUOTATIONS:CREATE", "QUOTATIONS:EDIT", "PDF:VIEW", "PDF:DOWNLOAD", "REVISIONS:VIEW",
                    "EMAIL:VIEW", "EMAIL:SEND", "WHATSAPP:VIEW", "WHATSAPP:SEND")) r.getRights().add(k);
            roles.save(r);
        }
        return admin;
    }
}
