package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Masters > Products: product setups (Genset, Solar, ...) with their parts. Rights come from the PRODUCTS module. */
@RestController @RequestMapping("/api/products") @RequiredArgsConstructor @Transactional
public class ProductController {
    private final ProductRepo repo;
    private final StorageService storage;
    private final AuditService audit;

    static final int MAX_PARTS = 200;

    record PartIn(String name, String specification, String make, String uom, BigDecimal qty, BigDecimal unitPrice, String imageKey) {}
    record Save(String businessLine, String name, String code, BigDecimal capacity, String description, Boolean active, List<PartIn> parts) {}

    /** ?line=GENSET narrows the list. The statement form reads it too, so quotation creators can see it without PRODUCTS. */
    @GetMapping
    public List<Product> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String line) {
        if (!me.can(AppModule.PRODUCTS, Right.VIEW) && !me.canAny(AppModule.QUOTATIONS, Right.CREATE, Right.EDIT))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view (" + AppModule.PRODUCTS.label + ")");
        List<Product> all = repo.findByTenantIdOrderByBusinessLineAscNameAsc(me.tenantId());
        if (line == null || line.isBlank()) return all;
        String l = BusinessLine.of(line).name();
        return all.stream().filter(p -> l.equals(p.getBusinessLine())).toList();
    }

    @GetMapping("/{id}") @Requires(module = AppModule.PRODUCTS, right = Right.VIEW)
    public Product get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { return find(id, me); }

    @PostMapping @Requires(module = AppModule.PRODUCTS, right = Right.CREATE)
    public Product create(@AuthenticationPrincipal AuthUser me, @RequestBody Save in) {
        Product p = new Product();
        p.setTenantId(me.tenantId());
        p.setCreatedBy(me.userId());
        p.setCreatedAt(Instant.now());
        apply(p, in, me);
        Product saved = repo.save(p);
        audit.log(me, AppModule.PRODUCTS, "CREATE", "Product", saved.getId(), summary(saved));
        return saved;
    }

    @PutMapping("/{id}") @Requires(module = AppModule.PRODUCTS, right = Right.EDIT)
    public Product update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody Save in) {
        Product p = find(id, me);
        apply(p, in, me);
        Product saved = repo.save(p);
        audit.log(me, AppModule.PRODUCTS, "EDIT", "Product", saved.getId(), summary(saved));
        return saved;
    }

    /** Copy of a product with " (copy)" in its name, to make a variant quickly. */
    @PostMapping("/{id}/clone") @Requires(module = AppModule.PRODUCTS, right = Right.CREATE)
    public Product duplicate(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Product src = find(id, me);
        String name = src.getName() + " (copy)";
        for (int n = 2; repo.findFirstByTenantIdAndBusinessLineAndNameIgnoreCase(me.tenantId(), src.getBusinessLine(), name).isPresent(); n++)
            name = src.getName() + " (copy " + n + ")";
        List<PartIn> parts = src.getParts().stream()
                .map(x -> new PartIn(x.getName(), x.getSpecification(), x.getMake(), x.getUom(), x.getQty(), x.getUnitPrice(), x.getImageKey())).toList();
        return create(me, new Save(src.getBusinessLine(), name, src.getCode(), src.getCapacity(), src.getDescription(), src.isActive(), parts));
    }

    /** Statements keep their own copy of the items, so deleting a product never changes an existing statement. */
    @DeleteMapping("/{id}") @Requires(module = AppModule.PRODUCTS, right = Right.DELETE)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Product p = find(id, me);
        audit.log(me, AppModule.PRODUCTS, "DELETE", "Product", p.getId(), summary(p));
        repo.delete(p);
    }

    private Product find(Long id, AuthUser me) {
        return repo.findByIdAndTenantId(id, me.tenantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private void apply(Product p, Save in, AuthUser me) {
        String name = trim(in.name());
        if (name == null) throw bad("Product name is required");
        BusinessLine line = BusinessLine.of(in.businessLine());
        repo.findFirstByTenantIdAndBusinessLineAndNameIgnoreCase(me.tenantId(), line.name(), name).filter(x -> !x.getId().equals(p.getId()))
                .ifPresent(x -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "A " + line.label + " product named \"" + name + "\" already exists"); });
        if (in.capacity() != null && in.capacity().signum() < 0) throw bad("Capacity cannot be negative");
        List<PartIn> parts = in.parts() == null ? List.of() : in.parts();
        if (parts.size() > MAX_PARTS) throw bad("A product can have at most " + MAX_PARTS + " parts");

        p.setBusinessLine(line.name());
        p.setName(name);
        p.setCode(trim(in.code()));
        p.setCapacity(in.capacity());
        p.setDescription(trim(in.description()));
        p.setActive(in.active() == null || in.active());
        p.setUpdatedAt(Instant.now());
        p.getParts().clear();
        int n = 1;
        for (PartIn x : parts) {
            String partName = trim(x.name());
            if (partName == null) continue;   // blank rows from the editor are dropped
            if (x.qty() != null && x.qty().signum() < 0) throw bad("Quantity of \"" + partName + "\" cannot be negative");
            if (x.unitPrice() != null && x.unitPrice().signum() < 0) throw bad("Unit price of \"" + partName + "\" cannot be negative");
            ProductPart part = new ProductPart();
            part.setSno(n++);
            part.setName(partName);
            part.setSpecification(trim(x.specification()));
            part.setMake(trim(x.make()));
            part.setUom(trim(x.uom()) == null ? "Nos." : x.uom().trim());
            part.setQty(x.qty() == null ? BigDecimal.ONE : x.qty());
            part.setUnitPrice(x.unitPrice() == null ? BigDecimal.ZERO : x.unitPrice());
            part.setImageKey(storage.own(me.tenantId(), x.imageKey()));
            part.setProduct(p);
            p.getParts().add(part);
        }
    }

    private static String summary(Product p) {
        return BusinessLine.of(p.getBusinessLine()).label + ": " + p.getName() + " (" + p.getParts().size() + " parts)";
    }
    private static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
