package com.saas.quote;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Administration: sign-in/out history, audit trail, and the overview numbers. */
@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor @Transactional(readOnly = true) @AdminOnly
public class AdminActivityController {
    private final ActivityRepo activity;
    private final SessionRepo sessions;
    private final UserRepo users;
    private final RoleRepo roles;

    record PageOut<T>(List<T> items, long total, int page, int size) {}
    record SessionDto(Long id, Long userId, String userName, String userEmail, String portal, Instant signedInAt, Instant signedOutAt,
                      String signOutReason, String ip, String userAgent) {}
    record Overview(long users, long activeUsers, long admins, long roles, long signInsToday, long actionsToday,
                    List<ActivityLog> recent) {}

    private static final DateTimeFormat.ISO ISO = DateTimeFormat.ISO.DATE_TIME;

    // ------------------------------------------------------------------ sign-in / sign-out history
    private Specification<LoginSession> sessionSpec(Long tid, Long userId, Instant from, Instant to) {
        return (r, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(r.get("tenantId"), tid));
            if (userId != null) p.add(cb.equal(r.get("userId"), userId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(r.get("signedInAt"), from));
            if (to != null) p.add(cb.lessThan(r.get("signedInAt"), to));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    @GetMapping("/activity/sessions")
    public PageOut<SessionDto> sessions(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        Page<LoginSession> p = sessions.findAll(sessionSpec(me.tenantId(), userId, from, to),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), Sort.by(Sort.Direction.DESC, "signedInAt")));
        return new PageOut<>(p.getContent().stream().map(s -> new SessionDto(s.getId(), s.getUserId(), s.getUserName(), s.getUserEmail(),
                s.getPortal(), s.getSignedInAt(), s.getSignedOutAt(), s.getSignOutReason(), s.getIp(), s.getUserAgent())).toList(),
                p.getTotalElements(), p.getNumber(), p.getSize());
    }

    // ------------------------------------------------------------------ audit trail
    private Specification<ActivityLog> auditSpec(Long tid, Long userId, String module, String action, String q, Instant from, Instant to) {
        return (r, cq, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(r.get("tenantId"), tid));
            if (userId != null) p.add(cb.equal(r.get("userId"), userId));
            if (module != null && !module.isBlank()) p.add(cb.equal(r.get("module"), module));
            if (action != null && !action.isBlank()) p.add(cb.equal(r.get("action"), action));
            if (from != null) p.add(cb.greaterThanOrEqualTo(r.get("at"), from));
            if (to != null) p.add(cb.lessThan(r.get("at"), to));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase().replace("%", "") + "%";
                p.add(cb.or(cb.like(cb.lower(r.get("detail")), like), cb.like(cb.lower(r.get("userName")), like), cb.like(cb.lower(r.get("entityType")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    @GetMapping("/activity/audit")
    public PageOut<ActivityLog> audit(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String module, @RequestParam(required = false) String action, @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        Page<ActivityLog> p = activity.findAll(auditSpec(me.tenantId(), userId, module, action, q, from, to),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), Sort.by(Sort.Direction.DESC, "at", "id")));
        return new PageOut<>(p.getContent(), p.getTotalElements(), p.getNumber(), p.getSize());
    }

    /** CSV export of the audit trail with the same filters (needs Activity Log "download"). */
    @GetMapping("/activity/audit.csv")
    public ResponseEntity<byte[]> auditCsv(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String module, @RequestParam(required = false) String action, @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        List<ActivityLog> rows = activity.findAll(auditSpec(me.tenantId(), userId, module, action, q, from, to),
                PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "at", "id"))).getContent();
        StringBuilder sb = new StringBuilder("\uFEFFTime (UTC),User,Module,Action,Record,Detail,IP\r\n");
        for (ActivityLog a : rows)
            sb.append(csv(String.valueOf(a.getAt()))).append(',').append(csv(a.getUserName())).append(',').append(csv(a.getModule())).append(',')
              .append(csv(a.getAction())).append(',').append(csv((a.getEntityType() == null ? "" : a.getEntityType()) + (a.getEntityId() == null ? "" : " #" + a.getEntityId())))
              .append(',').append(csv(a.getDetail())).append(',').append(csv(a.getIp())).append("\r\n");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-trail.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8")).body(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Quotes the value and neutralises spreadsheet formulas (=, +, -, @ at the start). */
    private static String csv(String v) {
        if (v == null) return "";
        String s = v.replace("\"", "\"\"");
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s + "\"";
    }

    // ------------------------------------------------------------------ overview
    @GetMapping("/overview")
    public Overview overview(@AuthenticationPrincipal AuthUser me) {
        List<AppUser> all = users.findByTenantIdOrderByIdDesc(me.tenantId());
        Instant midnight = java.time.LocalDate.now(java.time.ZoneOffset.UTC).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        long actionsToday = activity.count((r, q, cb) -> cb.and(cb.equal(r.get("tenantId"), me.tenantId()), cb.greaterThanOrEqualTo(r.get("at"), midnight)));
        return new Overview(all.size(), all.stream().filter(AppUser::enabled).count(), all.stream().filter(u -> u.isAdminUser() && u.enabled()).count(),
                roles.findByTenantIdOrderByName(me.tenantId()).size(), sessions.countByTenantIdAndSignedInAtAfter(me.tenantId(), midnight),
                actionsToday, activity.findTop8ByTenantIdOrderByIdDesc(me.tenantId()));
    }
}
