package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Administration: the module/right catalogue and role management. */
@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor @Transactional @AdminOnly
public class AdminRoleController {
    private final RoleRepo roles;
    private final UserRepo users;
    private final AuditService audit;

    record ModuleInfo(String key, String label, String group, boolean adminOnly, boolean ready, List<String> rights) {}
    record RoleDto(Long id, String name, String description, boolean systemRole, boolean adminRole, String quotationScope,
                   List<String> rights, long userCount) {}
    record Save(String name, String description, String quotationScope, List<String> rights) {}

    @GetMapping("/modules")
    public List<ModuleInfo> modules() {
        return Arrays.stream(AppModule.values())
                .map(m -> new ModuleInfo(m.name(), m.label, m.group, m.adminOnly, m.ready, m.rights.stream().map(Enum::name).toList())).toList();
    }

    @GetMapping("/roles")
    public List<RoleDto> list(@AuthenticationPrincipal AuthUser me) {
        return roles.findByTenantIdOrderByName(me.tenantId()).stream().map(this::dto).toList();
    }

    private RoleDto dto(Role r) {
        List<String> rights = r.isAdminRole() ? new ArrayList<>(AccessService.allKeys()) : r.getRights().stream().sorted().toList();
        return new RoleDto(r.getId(), r.getName(), r.getDescription(), r.isSystemRole(), r.isAdminRole(),
                r.isAdminRole() ? "ALL" : r.getQuotationScope(), rights, users.countByRole(r));
    }

    private Role find(Long id, AuthUser me) {
        return roles.findByIdAndTenantId(id, me.tenantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
    }

    private ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }

    private Set<String> cleanRights(List<String> in) {
        Set<String> out = new HashSet<>();
        if (in == null) return out;
        for (String k : in) {
            if (!AppModule.valid(k)) throw bad("Unknown right: " + k);
            if (AppModule.isAdminOnly(k)) throw bad("Administration rights belong to the Admin role only");
            out.add(k);
        }
        return out;
    }

    private void apply(Role r, Save in, AuthUser me) {
        String name = in.name() == null ? "" : in.name().trim();
        if (name.isEmpty()) throw bad("Role name is required");
        roles.findByTenantIdAndNameIgnoreCase(me.tenantId(), name).filter(x -> !x.getId().equals(r.getId()))
                .ifPresent(x -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "A role named \"" + name + "\" already exists"); });
        r.setName(name);
        r.setDescription(in.description());
        r.setQuotationScope("ALL".equals(in.quotationScope()) ? "ALL" : "OWN");
        r.getRights().clear();
        r.getRights().addAll(cleanRights(in.rights()));
    }

    @PostMapping("/roles")
    public RoleDto create(@AuthenticationPrincipal AuthUser me, @RequestBody Save in) {
        Role r = new Role();
        r.setTenantId(me.tenantId());
        apply(r, in, me);
        roles.save(r);
        audit.log(me, AppModule.ROLES, "CREATE", "Role", r.getId(), r.getName() + " with " + r.getRights().size() + " rights, quotations: " + r.getQuotationScope());
        return dto(r);
    }

    @PutMapping("/roles/{id}")
    public RoleDto update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody Save in) {
        Role r = find(id, me);
        if (r.isSystemRole()) throw bad("The Admin role is built in and cannot be changed");
        Set<String> before = new HashSet<>(r.getRights());
        apply(r, in, me);
        Set<String> added = new TreeSet<>(r.getRights()); added.removeAll(before);
        Set<String> removed = new TreeSet<>(before); removed.removeAll(r.getRights());
        roles.save(r);
        audit.log(me, AppModule.ROLES, "EDIT", "Role", r.getId(), r.getName() + ": added " + (added.isEmpty() ? "none" : added)
                + "; removed " + (removed.isEmpty() ? "none" : removed) + "; quotations: " + r.getQuotationScope());
        return dto(r);
    }

    @DeleteMapping("/roles/{id}")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Role r = find(id, me);
        if (r.isSystemRole()) throw bad("The Admin role cannot be deleted");
        long n = users.countByRole(r);
        if (n > 0) throw bad(n + " user(s) still have this role. Move them to another role first.");
        audit.log(me, AppModule.ROLES, "DELETE", "Role", r.getId(), r.getName());
        roles.delete(r);
    }
}
