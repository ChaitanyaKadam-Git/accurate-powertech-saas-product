package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Masters > Parts. Part of the product master, so it uses the PRODUCTS rights. */
@RestController @RequestMapping("/api/parts") @RequiredArgsConstructor @Transactional
public class PartController {
    private final PartRepo repo;
    private final StorageService storage;
    private final AuditService audit;

    record Save(String name, String category, String businessLine, String specification, String make, String uom,
                BigDecimal unitPrice, String hsnCode, String imageKey, Boolean active) {}
    /** A part plus how many products use it. */
    record Row(Long id, String name, String category, String businessLine, String specification, String make, String uom,
               BigDecimal unitPrice, String hsnCode, String imageKey, boolean active, Instant updatedAt, long usedIn) {}

    /** ?line=GENSET gives the parts a genset product may use (GENSET and BOTH). Quotation creators may read it too. */
    @GetMapping
    public List<Row> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String line) {
        if (!me.can(AppModule.PRODUCTS, Right.VIEW) && !me.canAny(AppModule.QUOTATIONS, Right.CREATE, Right.EDIT))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view (" + AppModule.PRODUCTS.label + ")");
        Map<Long, Long> used = new HashMap<>();
        for (Object[] r : repo.usage(me.tenantId())) used.put((Long) r[0], (Long) r[1]);
        String l = line == null || line.isBlank() ? null : BusinessLine.of(line).name();
        return repo.findByTenantIdOrderByCategoryAscNameAsc(me.tenantId()).stream()
                .filter(p -> l == null || p.fits(l))
                .map(p -> row(p, used.getOrDefault(p.getId(), 0L))).toList();
    }

    @PostMapping @Requires(module = AppModule.PRODUCTS, right = Right.CREATE)
    public Row create(@AuthenticationPrincipal AuthUser me, @RequestBody Save in) {
        Part p = new Part();
        p.setTenantId(me.tenantId());
        p.setCreatedBy(me.userId());
        p.setCreatedAt(Instant.now());
        apply(p, in, me);
        Part saved = repo.save(p);
        audit.log(me, AppModule.PRODUCTS, "CREATE", "Part", saved.getId(), describe(saved));
        return row(saved, 0);
    }

    /** A new price or detail applies to every product that uses the part (statements already made keep their copy). */
    @PutMapping("/{id}") @Requires(module = AppModule.PRODUCTS, right = Right.EDIT)
    public Row update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody Save in) {
        Part p = find(id, me);
        BigDecimal before = p.getUnitPrice();
        apply(p, in, me);
        List<String> users = repo.usedBy(p.getId());
        String change = before != null && p.getUnitPrice() != null && before.compareTo(p.getUnitPrice()) != 0
                ? "; price " + before.stripTrailingZeros().toPlainString() + " -> " + p.getUnitPrice().stripTrailingZeros().toPlainString() + " in " + users.size() + " product(s)" : "";
        audit.log(me, AppModule.PRODUCTS, "EDIT", "Part", p.getId(), describe(p) + change);
        return row(repo.save(p), users.size());
    }

    @DeleteMapping("/{id}") @Requires(module = AppModule.PRODUCTS, right = Right.DELETE)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Part p = find(id, me);
        List<String> users = repo.usedBy(p.getId());
        if (!users.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "\"" + p.getName() + "\" is used in " + users.size() + " product(s): "
                    + String.join(", ", users.subList(0, Math.min(5, users.size()))) + (users.size() > 5 ? " ..." : "")
                    + ". Remove it from those products first, or mark it inactive.");
        audit.log(me, AppModule.PRODUCTS, "DELETE", "Part", p.getId(), describe(p));
        repo.delete(p);
    }

    private Part find(Long id, AuthUser me) {
        return repo.findByIdAndTenantId(id, me.tenantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found"));
    }

    private void apply(Part p, Save in, AuthUser me) {
        String name = trim(in.name());
        if (name == null) throw bad("Part name is required");
        String make = trim(in.make());
        repo.findSame(me.tenantId(), name, make).stream().filter(x -> !x.getId().equals(p.getId())).findFirst()
                .ifPresent(x -> { throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The part \"" + name + "\"" + (make == null ? "" : " by " + make) + " already exists"); });
        if (in.unitPrice() != null && in.unitPrice().signum() < 0) throw bad("Unit price cannot be negative");
        String line = trim(in.businessLine()) == null || Part.BOTH.equalsIgnoreCase(in.businessLine().trim()) ? Part.BOTH : BusinessLine.of(in.businessLine()).name();
        String hsn = trim(in.hsnCode());
        if (hsn != null && !hsn.matches("\\d{4,8}")) throw bad("HSN / SAC code must be 4 to 8 digits");
        p.setName(name);
        p.setCategory(trim(in.category()));
        p.setBusinessLine(line);
        p.setSpecification(trim(in.specification()));
        p.setMake(make);
        p.setUom(trim(in.uom()) == null ? "Nos." : in.uom().trim());
        p.setUnitPrice(in.unitPrice() == null ? BigDecimal.ZERO : in.unitPrice());
        p.setHsnCode(hsn);
        p.setImageKey(storage.own(me.tenantId(), in.imageKey()));
        p.setActive(in.active() == null || in.active());
        p.setUpdatedAt(Instant.now());
    }

    private static Row row(Part p, long usedIn) {
        return new Row(p.getId(), p.getName(), p.getCategory(), p.getBusinessLine(), p.getSpecification(), p.getMake(), p.getUom(),
                p.getUnitPrice(), p.getHsnCode(), p.getImageKey(), p.isActive(), p.getUpdatedAt(), usedIn);
    }
    private static String describe(Part p) { return p.getName() + (p.getMake() == null ? "" : " (" + p.getMake() + ")"); }
    private static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
