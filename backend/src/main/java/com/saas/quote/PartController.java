package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Masters > Parts. Uses the PARTS rights. */
@RestController @RequestMapping("/api/parts") @RequiredArgsConstructor @Transactional
public class PartController {
    private final PartRepo repo;
    private final StorageService storage;
    private final AuditService audit;
    private final PartImportService importer;

    record Save(String name, String category, String businessLine, String specification, String make, String uom,
                BigDecimal unitPrice, String hsnCode, String imageKey, Boolean active) {}
    /** A part plus how many products use it. */
    record Row(Long id, String name, String category, String businessLine, String specification, String make, String uom,
               BigDecimal unitPrice, String hsnCode, String imageKey, boolean active, Instant updatedAt, long usedIn) {}

    /** ?line=GENSET gives the parts a genset product may use (GENSET and BOTH). Quotation creators may read it too. */
    @GetMapping
    public List<Row> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String line) {
        if (!me.can(AppModule.PARTS, Right.VIEW) && !me.can(AppModule.PRODUCTS, Right.VIEW) && !me.canAny(AppModule.QUOTATIONS, Right.CREATE, Right.EDIT))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view (" + AppModule.PARTS.label + ")");
        Map<Long, Long> used = new HashMap<>();
        for (Object[] r : repo.usage(me.tenantId())) used.put((Long) r[0], (Long) r[1]);
        String l = line == null || line.isBlank() ? null : BusinessLine.of(line).name();
        return repo.findByTenantIdOrderByCategoryAscNameAsc(me.tenantId()).stream()
                .filter(p -> l == null || p.fits(l))
                .map(p -> row(p, used.getOrDefault(p.getId(), 0L))).toList();
    }

    @PostMapping @Requires(module = AppModule.PARTS, right = Right.CREATE)
    public Row create(@AuthenticationPrincipal AuthUser me, @RequestBody Save in) {
        Part p = new Part();
        p.setTenantId(me.tenantId());
        p.setCreatedBy(me.userId());
        p.setCreatedAt(Instant.now());
        apply(p, in, me);
        Part saved = repo.save(p);
        audit.log(me, AppModule.PARTS, "CREATE", "Part", saved.getId(), describe(saved));
        return row(saved, 0);
    }

    /** A new price or detail applies to every product that uses the part (statements already made keep their copy). */
    @PutMapping("/{id}") @Requires(module = AppModule.PARTS, right = Right.EDIT)
    public Row update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody Save in) {
        Part p = find(id, me);
        BigDecimal before = p.getUnitPrice();
        apply(p, in, me);
        List<String> users = repo.usedBy(p.getId());
        String change = before != null && p.getUnitPrice() != null && before.compareTo(p.getUnitPrice()) != 0
                ? "; price " + before.stripTrailingZeros().toPlainString() + " -> " + p.getUnitPrice().stripTrailingZeros().toPlainString() + " in " + users.size() + " product(s)" : "";
        audit.log(me, AppModule.PARTS, "EDIT", "Part", p.getId(), describe(p) + change);
        return row(repo.save(p), users.size());
    }

    @DeleteMapping("/{id}") @Requires(module = AppModule.PARTS, right = Right.DELETE)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Part p = find(id, me);
        List<String> users = repo.usedBy(p.getId());
        if (!users.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "\"" + p.getName() + "\" is used in " + users.size() + " product(s): "
                    + String.join(", ", users.subList(0, Math.min(5, users.size()))) + (users.size() > 5 ? " ..." : "")
                    + ". Remove it from those products first, or mark it inactive.");
        audit.log(me, AppModule.PARTS, "DELETE", "Part", p.getId(), describe(p));
        repo.delete(p);
    }

    // ------------------------------------------------------------------ import from Excel / CSV
    record ImportRow(int row, String name, String make, String category, String uom, BigDecimal unitPrice, String line,
                     String status, List<String> errors) {}   // status: NEW | UPDATE | ERROR
    record ImportReport(boolean committed, boolean update, int total, int newCount, int updateCount, int invalid,
                        int created, int updated, List<ImportRow> rows) {}

    @GetMapping("/import/template") @Requires(module = AppModule.PARTS, right = Right.CREATE)
    public ResponseEntity<byte[]> template(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "xlsx") String format) throws IOException {
        boolean csv = "csv".equalsIgnoreCase(format);
        List<String> cats = repo.findByTenantIdOrderByCategoryAscNameAsc(me.tenantId()).stream()
                .map(Part::getCategory).filter(c -> c != null && !c.isBlank()).distinct().sorted().toList();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"parts-import-template." + (csv ? "csv" : "xlsx") + "\"")
                .contentType(csv ? MediaType.parseMediaType("text/csv") : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(csv ? importer.templateCsv() : importer.templateXlsx(cats));
    }

    /**
     * commit=false: check only, nothing is saved. commit=true: create the NEW rows and, with update=true, update the parts
     * that already exist (same name + make). Rows with errors are always skipped.
     */
    @PostMapping("/import") @Requires(module = AppModule.PARTS, right = Right.CREATE)
    public ImportReport importParts(@AuthenticationPrincipal AuthUser me, @RequestParam("file") MultipartFile file,
                                    @RequestParam(defaultValue = "false") boolean commit, @RequestParam(defaultValue = "false") boolean update) {
        if (update && !me.can(AppModule.PARTS, Right.EDIT))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to edit (" + AppModule.PARTS.label + ")");
        List<PartImportService.Row> in = importer.read(file);
        Set<String> seen = new HashSet<>();
        List<ImportRow> out = new ArrayList<>();
        int news = 0, updates = 0, created = 0, updated = 0, priceChanges = 0;
        for (PartImportService.Row r : in) {
            List<String> errs = new ArrayList<>(r.errors());
            String make = r.make().isBlank() ? null : r.make();
            Part existing = null;
            if (!r.name().isBlank()) {
                if (!seen.add(r.name().toLowerCase() + "|" + (make == null ? "" : make.toLowerCase()))) errs.add("Same part name and make appear earlier in the file");
                else existing = repo.findSame(me.tenantId(), r.name(), make).stream().findFirst().orElse(null);
                if (existing != null && !update) errs.add("Part already exists. Tick \"Update existing parts\" to change it");
            }
            String status = !errs.isEmpty() ? "ERROR" : existing != null ? "UPDATE" : "NEW";
            if (status.equals("NEW")) news++;
            if (status.equals("UPDATE")) updates++;
            if (commit && !status.equals("ERROR")) {
                Part p = existing != null ? existing : new Part();
                if (existing == null) { p.setTenantId(me.tenantId()); p.setCreatedBy(me.userId()); p.setCreatedAt(Instant.now()); p.setActive(true); }
                else if (p.getUnitPrice() == null || p.getUnitPrice().compareTo(r.unitPrice()) != 0) priceChanges++;
                p.setName(r.name());
                p.setMake(make);
                if (!r.category().isBlank() || existing == null) p.setCategory(blankToNull(r.category()));
                if (!r.specification().isBlank() || existing == null) p.setSpecification(blankToNull(r.specification()));
                p.setUom(r.uom().isBlank() ? (existing != null && p.getUom() != null ? p.getUom() : "Nos.") : r.uom());
                p.setUnitPrice(r.unitPrice());
                if (!r.hsn().isBlank() || existing == null) p.setHsnCode(blankToNull(r.hsn()));
                p.setBusinessLine(r.line());
                p.setUpdatedAt(Instant.now());
                repo.save(p);
                if (existing == null) created++; else updated++;
            }
            out.add(new ImportRow(r.row(), r.name(), make, blankToNull(r.category()), blankToNull(r.uom()), r.unitPrice(), r.line(), status, errs));
        }
        int invalid = in.size() - news - updates;
        if (commit) audit.log(me, AppModule.PARTS, "IMPORT", "Part", null, file.getOriginalFilename() + ": " + created + " created, " + updated
                + " updated (" + priceChanges + " price change(s)), " + invalid + " skipped");
        return new ImportReport(commit, update, in.size(), news, updates, invalid, created, updated, out);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

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
