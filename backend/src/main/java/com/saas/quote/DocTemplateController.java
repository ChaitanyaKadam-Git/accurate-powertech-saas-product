package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;

/** Masters > Templates. Uses the "Templates" rights (TEMPLATE_DATA); readable by anyone who creates or edits statements. */
@RestController @RequestMapping("/api/templates") @RequiredArgsConstructor @Transactional
public class DocTemplateController {
    private final DocTemplateRepo repo;
    private final DocTemplateService service;
    private final AuditService audit;

    record SectionIn(String kind, String title, String content, String style, Boolean visible) {}
    record Save(String docType, String businessLine, String name, Boolean isDefault, Boolean active, String pdfTitle,
                Boolean showCover, Boolean showLetter, Boolean showPrices, String description, List<SectionIn> sections) {}

    @GetMapping
    public List<DocTemplate> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String type, @RequestParam(required = false) String line) {
        if (!me.can(AppModule.TEMPLATE_DATA, Right.VIEW) && !me.canAny(AppModule.QUOTATIONS, Right.CREATE, Right.EDIT))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view (" + AppModule.TEMPLATE_DATA.label + ")");
        service.ensureDefaults(me.tenantId());
        return repo.findByTenantIdOrderByDocTypeAscBusinessLineAscNameAsc(me.tenantId()).stream()
                .filter(t -> type == null || type.isBlank() || t.getDocType().equals(DocType.of(type).name()))
                .filter(t -> line == null || line.isBlank() || t.getBusinessLine().equals(BusinessLine.of(line).name())).toList();
    }

    @GetMapping("/{id}")
    public DocTemplate get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        if (!me.can(AppModule.TEMPLATE_DATA, Right.VIEW) && !me.canAny(AppModule.QUOTATIONS, Right.CREATE, Right.EDIT))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return find(id, me);
    }

    @PostMapping @Requires(module = AppModule.TEMPLATE_DATA, right = Right.CREATE)
    public DocTemplate create(@AuthenticationPrincipal AuthUser me, @RequestBody Save in) {
        service.ensureDefaults(me.tenantId());
        DocTemplate t = new DocTemplate();
        t.setTenantId(me.tenantId()); t.setCreatedBy(me.userId()); t.setCreatedAt(Instant.now());
        apply(t, in, me);
        repo.save(t);
        audit.log(me, AppModule.TEMPLATE_DATA, "CREATE", "Template", t.getId(), describe(t));
        return t;
    }

    @PutMapping("/{id}") @Requires(module = AppModule.TEMPLATE_DATA, right = Right.EDIT)
    public DocTemplate update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody Save in) {
        DocTemplate t = find(id, me);
        apply(t, in, me);
        audit.log(me, AppModule.TEMPLATE_DATA, "EDIT", "Template", t.getId(), describe(t));
        return repo.save(t);
    }

    @PostMapping("/{id}/clone") @Requires(module = AppModule.TEMPLATE_DATA, right = Right.CREATE)
    public DocTemplate duplicate(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        DocTemplate s = find(id, me);
        List<SectionIn> secs = s.getSections().stream().map(x -> new SectionIn(x.getKind(), x.getTitle(), x.getContent(), x.getStyle(), x.isVisible())).toList();
        return create(me, new Save(s.getDocType(), s.getBusinessLine(), s.getName() + " (copy)", false, true, s.getPdfTitle(), s.isShowCover(),
                s.isShowLetter(), s.isShowPrices(), s.getDescription(), secs));
    }

    /** Documents keep their own copy, so deleting a template changes nothing already made. The last one of a type and line stays. */
    @DeleteMapping("/{id}") @Requires(module = AppModule.TEMPLATE_DATA, right = Right.DELETE)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        DocTemplate t = find(id, me);
        List<DocTemplate> same = repo.findByTenantIdAndDocTypeAndBusinessLine(me.tenantId(), t.getDocType(), t.getBusinessLine());
        if (same.size() <= 1) throw bad("This is the only " + label(t) + " template. Add another one before deleting it.");
        if (t.isDefault()) same.stream().filter(x -> !x.getId().equals(t.getId())).findFirst().ifPresent(x -> x.setDefault(true));
        audit.log(me, AppModule.TEMPLATE_DATA, "DELETE", "Template", t.getId(), describe(t));
        repo.delete(t);
    }

    private DocTemplate find(Long id, AuthUser me) {
        return repo.findByIdAndTenantId(id, me.tenantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
    }

    private void apply(DocTemplate t, Save in, AuthUser me) {
        String name = trim(in.name());
        if (name == null) throw bad("Template name is required");
        DocType type = DocType.of(in.docType());
        BusinessLine line = BusinessLine.of(in.businessLine());
        List<DocTemplate> same = repo.findByTenantIdAndDocTypeAndBusinessLine(me.tenantId(), type.name(), line.name());
        same.stream().filter(x -> !x.getId().equals(t.getId()) && x.getName().equalsIgnoreCase(name)).findFirst()
                .ifPresent(x -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "A " + type.label.toLowerCase() + " template named \"" + name + "\" already exists for " + line.label); });
        List<SectionIn> secs = in.sections() == null ? List.of() : in.sections();
        if (secs.size() > 60) throw bad("A template can have at most 60 sections");
        t.setDocType(type.name()); t.setBusinessLine(line.name()); t.setName(name);
        t.setActive(in.active() == null || in.active());
        t.setPdfTitle(trim(in.pdfTitle())); t.setDescription(trim(in.description()));
        t.setShowCover(in.showCover() == null || in.showCover());
        t.setShowLetter(in.showLetter() == null || in.showLetter());
        t.setShowPrices(in.showPrices() == null || in.showPrices());
        t.setUpdatedAt(Instant.now());
        t.getSections().clear();
        int p = 0;
        for (SectionIn s : secs) {
            TemplateSection x = new TemplateSection();
            x.setPosition(p++);
            x.setKind(s.kind() != null && List.of("TEXT", "BOM", "OFFER").contains(s.kind()) ? s.kind() : "TEXT");
            x.setTitle(trim(s.title()) == null ? "Section" : s.title().trim());
            x.setContent(s.content());
            x.setStyle(s.style() != null && List.of("BULLETS", "PARAGRAPH", "TABLE").contains(s.style()) ? s.style() : "BULLETS");
            x.setVisible(s.visible() == null || s.visible());
            x.setTemplate(t);
            t.getSections().add(x);
        }
        // exactly one default per type and line
        boolean wantDefault = Boolean.TRUE.equals(in.isDefault()) || same.stream().noneMatch(x -> !x.getId().equals(t.getId()) && x.isDefault());
        if (wantDefault) { same.forEach(x -> { if (!x.getId().equals(t.getId())) x.setDefault(false); }); t.setDefault(true); }
        else if (Boolean.FALSE.equals(in.isDefault())) t.setDefault(false);
        if (!t.isActive() && t.isDefault()) throw bad("The default template must be active. Make another template the default first.");
    }

    private static String label(DocTemplate t) { return BusinessLine.of(t.getBusinessLine()).label + " " + DocType.of(t.getDocType()).label.toLowerCase(); }
    private static String describe(DocTemplate t) { return label(t) + ": " + t.getName() + (t.isDefault() ? " (default)" : ""); }
    private static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
