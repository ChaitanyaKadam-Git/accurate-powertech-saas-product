package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Administration: users, bulk import, activation, password reset and per-user permissions. */
@RestController @RequestMapping("/api/admin/users") @RequiredArgsConstructor @Transactional @AdminOnly
public class AdminUserController {
    private final UserRepo users;
    private final RoleRepo roles;
    private final SessionRepo sessions;
    private final AccessService access;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final UserImportService importer;

    private static final SecureRandom RNG = new SecureRandom();

    record UserDto(Long id, String name, String email, String mobile, String designation, String department,
                   Long roleId, String roleName, boolean admin, boolean active, boolean mustChangePassword,
                   Instant createdAt, Instant lastLoginAt, int grantCount, int revokeCount, int permissionCount) {}
    record Save(String name, String email, String mobile, String designation, String department, Long roleId) {}
    record Created(UserDto user, String tempPassword) {}
    record Active(boolean active) {}
    record Rights(List<String> rights) {}
    record Detail(UserDto user, List<String> rolePermissions, List<String> permissions, List<String> grants, List<String> revokes) {}

    private UserDto dto(AppUser u) {
        Role r = u.getRole();
        return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getMobile(), u.getDesignation(), u.getDepartment(),
                r == null ? null : r.getId(), r == null ? "" : r.getName(), u.isAdminUser(), u.enabled(), u.mustChange(),
                u.getCreatedAt(), u.getLastLoginAt(), u.getGrants().size(), u.getRevokes().size(), access.effective(u).size());
    }

    private AppUser find(Long id, AuthUser me) {
        return users.findByIdAndTenantId(id, me.tenantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Role role(Long id, AuthUser me) {
        return roles.findByIdAndTenantId(id, me.tenantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid role"));
    }

    private ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }

    // ------------------------------------------------------------------ list / view
    @GetMapping
    public List<UserDto> list(@AuthenticationPrincipal AuthUser me) {
        return users.findByTenantIdOrderByIdDesc(me.tenantId()).stream().map(this::dto).toList();
    }

    @GetMapping("/{id}")
    public Detail get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        AppUser u = find(id, me);
        return new Detail(dto(u), sorted(access.roleBase(u.getRole())), sorted(access.effective(u)), sorted(u.getGrants()), sorted(u.getRevokes()));
    }

    private static List<String> sorted(Collection<String> c) { return c.stream().sorted().toList(); }

    // ------------------------------------------------------------------ create / edit
    @PostMapping
    public Created create(@AuthenticationPrincipal AuthUser me, @RequestBody Save in) {
        Role r = in.roleId() == null ? null : role(in.roleId(), me);
        List<String> errs = UserImportService.check(in.name(), in.email(), in.mobile(), r == null ? "" : r.getName());
        if (!errs.isEmpty()) throw bad(String.join(". ", errs));
        String email = in.email().trim().toLowerCase();
        if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already used by another account");
        assertMobileFree(me.tenantId(), in.mobile(), null);
        String temp = tempPassword();
        AppUser u = build(me, in.name(), email, in.mobile(), in.designation(), in.department(), r, temp);
        audit.log(me, AppModule.USERS, "CREATE", "User", u.getId(), u.getName() + " <" + email + "> as " + r.getName());
        return new Created(dto(u), temp);
    }

    private AppUser build(AuthUser me, String name, String email, String mobile, String desig, String dept, Role r, String temp) {
        AppUser u = new AppUser();
        u.setEmail(email); u.setPassword(encoder.encode(temp)); u.setTenantId(me.tenantId()); u.setRole(r);
        u.setName(name.trim()); u.setMobile(mobile.trim());
        u.setDesignation(blankToNull(desig)); u.setDepartment(blankToNull(dept));
        u.setActive(true); u.setMustChangePassword(true); u.setCreatedAt(Instant.now());
        return users.save(u);
    }

    @PutMapping("/{id}")
    public UserDto update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody Save in) {
        AppUser u = find(id, me);
        Role r = in.roleId() == null ? u.getRole() : role(in.roleId(), me);
        List<String> errs = UserImportService.check(in.name(), in.email(), in.mobile(), r == null ? "" : r.getName());
        if (!errs.isEmpty()) throw bad(String.join(". ", errs));
        String email = in.email().trim().toLowerCase();
        if (!email.equals(u.getEmail()) && users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already used by another account");
        assertMobileFree(me.tenantId(), in.mobile(), u.getId());
        boolean roleChanged = u.getRole() == null || !u.getRole().getId().equals(r.getId());
        if (roleChanged && u.isAdminUser() && !r.isAdminRole()) assertNotLastAdmin(me, u, "change the role of");
        if (roleChanged && u.getId().equals(me.userId())) throw bad("You cannot change your own role");
        String before = u.getName() + " / " + u.getEmail() + " / " + (u.getRole() == null ? "-" : u.getRole().getName());
        u.setName(in.name().trim()); u.setEmail(email); u.setMobile(in.mobile().trim());
        u.setDesignation(blankToNull(in.designation())); u.setDepartment(blankToNull(in.department()));
        u.setRole(r);
        if (roleChanged) { u.getGrants().clear(); u.getRevokes().clear(); }   // overrides were relative to the old role
        users.save(u);
        if (roleChanged) sessions.closeAll(u.getId(), Instant.now(), "ROLE_CHANGED");   // they sign in again on the right portal
        audit.log(me, AppModule.USERS, "EDIT", "User", u.getId(), "Was: " + before + ". Now: " + u.getName() + " / " + email + " / " + r.getName());
        return dto(u);
    }

    private void assertMobileFree(Long tenantId, String mobile, Long exceptId) {
        String d = UserImportService.digits(mobile);
        for (AppUser x : users.findByTenantIdOrderByIdDesc(tenantId))
            if (!x.getId().equals(exceptId) && d.equals(UserImportService.digits(x.getMobile())))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "That mobile number is already used by " + x.displayName());
    }

    // ------------------------------------------------------------------ activate / deactivate / reset
    @PostMapping("/{id}/active")
    public UserDto setActive(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody Active in) {
        AppUser u = find(id, me);
        if (!in.active()) {
            if (u.getId().equals(me.userId())) throw bad("You cannot deactivate your own account");
            if (u.isAdminUser()) assertNotLastAdmin(me, u, "deactivate");
        }
        u.setActive(in.active());
        users.save(u);
        if (!in.active()) sessions.closeAll(u.getId(), Instant.now(), "DEACTIVATED");
        audit.log(me, AppModule.USERS, in.active() ? "ACTIVATE" : "DEACTIVATE", "User", u.getId(), u.getName() + " <" + u.getEmail() + ">");
        return dto(u);
    }

    private void assertNotLastAdmin(AuthUser me, AppUser target, String verb) {
        long others = users.findByTenantIdOrderByIdDesc(me.tenantId()).stream()
                .filter(x -> !x.getId().equals(target.getId()) && x.isAdminUser() && x.enabled()).count();
        if (others == 0) throw bad("You cannot " + verb + " the only active administrator");
    }

    /** Admin-initiated reset: a new temporary password (shown once); the person must choose their own at next sign-in. */
    @PostMapping("/{id}/reset-password")
    public Map<String, String> resetPassword(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        AppUser u = find(id, me);
        String temp = tempPassword();
        u.setPassword(encoder.encode(temp));
        u.setMustChangePassword(true);
        users.save(u);
        sessions.closeAll(u.getId(), Instant.now(), "PASSWORD_RESET");
        audit.log(me, AppModule.USERS, "RESET_PASSWORD", "User", u.getId(), u.getName() + " <" + u.getEmail() + ">");
        return Map.of("tempPassword", temp);
    }

    // ------------------------------------------------------------------ permissions (grant / revoke)
    /**
     * Receives the full list of rights the person should end up with. Anything the role does not give is stored as a grant,
     * anything the role gives but is missing from the list is stored as a revoke.
     */
    @PutMapping("/{id}/permissions")
    public Detail setPermissions(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody Rights in) {
        AppUser u = find(id, me);
        if (u.isAdminUser()) throw bad("Administrators always have every right");
        Set<String> want = new HashSet<>();
        for (String k : in.rights() == null ? List.<String>of() : in.rights()) {
            if (!AppModule.valid(k)) throw bad("Unknown right: " + k);
            if (AppModule.isAdminOnly(k)) throw bad("Administration rights cannot be granted to non-administrators");
            want.add(k);
        }
        Set<String> base = access.roleBase(u.getRole());
        Set<String> beforeEff = access.effective(u);
        Set<String> newGrants = new HashSet<>(want); newGrants.removeAll(base);
        Set<String> newRevokes = new HashSet<>(base); newRevokes.removeAll(want);
        u.getGrants().clear(); u.getGrants().addAll(newGrants);
        u.getRevokes().clear(); u.getRevokes().addAll(newRevokes);
        users.save(u);
        Set<String> added = new TreeSet<>(want); added.removeAll(beforeEff);
        Set<String> removed = new TreeSet<>(beforeEff); removed.removeAll(want);
        audit.log(me, AppModule.USERS, "PERMISSIONS", "User", u.getId(),
                u.getName() + ": granted " + (added.isEmpty() ? "none" : added) + "; revoked " + (removed.isEmpty() ? "none" : removed));
        return get(me, id);
    }

    @PostMapping("/{id}/permissions/reset")
    public Detail resetPermissions(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        AppUser u = find(id, me);
        u.getGrants().clear(); u.getRevokes().clear();
        users.save(u);
        audit.log(me, AppModule.USERS, "PERMISSIONS", "User", u.getId(), u.getName() + ": back to the rights of role " + (u.getRole() == null ? "-" : u.getRole().getName()));
        return get(me, id);
    }

    // ------------------------------------------------------------------ bulk import
    record RowResult(int row, String name, String email, String mobile, String designation, String department, String role,
                     List<String> errors, String tempPassword) {}
    record Report(boolean committed, int total, int valid, int invalid, int created, List<RowResult> rows) {}

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "xlsx") String format) throws IOException {
        List<String> names = roles.findByTenantIdOrderByName(me.tenantId()).stream().map(Role::getName).toList();
        boolean csv = "csv".equalsIgnoreCase(format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"user-import-template." + (csv ? "csv" : "xlsx") + "\"")
                .contentType(csv ? MediaType.parseMediaType("text/csv") : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(csv ? importer.templateCsv(names) : importer.templateXlsx(names));
    }

    /** commit=false: validate only and return the row-by-row report. commit=true: create every valid row (invalid rows are skipped). */
    @PostMapping("/import")
    public Report importUsers(@AuthenticationPrincipal AuthUser me, @RequestParam("file") MultipartFile file,
                              @RequestParam(defaultValue = "false") boolean commit) {
        List<UserImportService.Row> in = importer.read(file);
        Map<String, Role> byName = new HashMap<>();
        for (Role r : roles.findByTenantIdOrderByName(me.tenantId())) byName.put(r.getName().toLowerCase(), r);
        Set<String> seenEmail = new HashSet<>(), seenMobile = new HashSet<>();
        Set<String> dbMobile = new HashSet<>();
        for (AppUser x : users.findByTenantIdOrderByIdDesc(me.tenantId())) if (x.getMobile() != null) dbMobile.add(UserImportService.digits(x.getMobile()));

        List<RowResult> out = new ArrayList<>();
        int valid = 0, created = 0;
        for (UserImportService.Row r : in) {
            List<String> errs = new ArrayList<>(UserImportService.check(r.name(), r.email(), r.mobile(), r.role()));
            Role role = r.role().isBlank() ? null : byName.get(r.role().toLowerCase());
            if (!r.role().isBlank() && role == null) errs.add("Role \"" + r.role() + "\" does not exist");
            String email = r.email().toLowerCase(), mob = UserImportService.digits(r.mobile());
            if (!email.isBlank()) {
                if (!seenEmail.add(email)) errs.add("Duplicate email within the file");
                else if (users.existsByEmail(email)) errs.add("Email is already registered");
            }
            if (!mob.isBlank()) {
                if (!seenMobile.add(mob)) errs.add("Duplicate mobile within the file");
                else if (dbMobile.contains(mob)) errs.add("Mobile number is already used by another user");
            }
            String temp = null;
            if (errs.isEmpty()) {
                valid++;
                if (commit) {
                    temp = tempPassword();
                    AppUser u = build(me, r.name(), email, r.mobile(), r.designation(), r.department(), role, temp);
                    audit.log(me, AppModule.USERS, "CREATE", "User", u.getId(), "Bulk import row " + r.row() + ": " + u.getName() + " as " + role.getName());
                    created++;
                }
            }
            out.add(new RowResult(r.row(), r.name(), r.email(), r.mobile(), r.designation(), r.department(), r.role(), errs, temp));
        }
        if (commit) audit.log(me, AppModule.USERS, "IMPORT", "User", null, file.getOriginalFilename() + ": " + created + " created, " + (in.size() - valid) + " rejected");
        return new Report(commit, in.size(), valid, in.size() - valid, created, out);
    }

    // ------------------------------------------------------------------ helpers
    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** 10 characters from an alphabet without look-alikes (no 0/O, 1/l/I), always with upper, lower and a digit. */
    static String tempPassword() {
        String up = "ABCDEFGHJKLMNPQRSTUVWXYZ", lo = "abcdefghijkmnpqrstuvwxyz", di = "23456789", all = up + lo + di;
        List<Character> c = new ArrayList<>();
        c.add(up.charAt(RNG.nextInt(up.length()))); c.add(lo.charAt(RNG.nextInt(lo.length()))); c.add(di.charAt(RNG.nextInt(di.length())));
        while (c.size() < 10) c.add(all.charAt(RNG.nextInt(all.length())));
        Collections.shuffle(c, RNG);
        return c.stream().map(String::valueOf).collect(Collectors.joining());
    }
}
