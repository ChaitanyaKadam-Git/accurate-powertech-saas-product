package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController @RequestMapping("/api/quotations") @RequiredArgsConstructor @Transactional
public class QuotationController {
    private final QuotationRepo repo;
    private final PdfService pdf;
    private final StorageService storage;
    private final TemplateService templates;
    private final UserRepo users;
    private final AuditService audit;
    private final EnquiryController enquiries;

    record Summary(Long id, String businessLine, Long enquiryId, String refNo, String clientName, String projectLocation, LocalDate quoteDate, BigDecimal total, String createdBy) {}

    /** Quotations this person may see: everything in the company, or only their own, depending on the role. */
    @GetMapping @Requires(module = AppModule.QUOTATIONS, right = Right.VIEW)
    public List<Summary> list(@AuthenticationPrincipal AuthUser me) {
        Map<Long, String> names = new HashMap<>();
        for (AppUser u : users.findByTenantIdOrderByIdDesc(me.tenantId())) names.put(u.getId(), u.displayName());
        List<Quotation> rows = me.seesAllQuotations() ? repo.findByOwnerIdOrderByIdDesc(me.tenantId())
                : repo.findByOwnerIdAndCreatedByOrderByIdDesc(me.tenantId(), me.userId());
        return rows.stream()
                .map(q -> new Summary(q.getId(), q.getBusinessLine(), q.getEnquiryId(), q.getRefNo(), q.getClientName(), q.getProjectLocation(), q.getQuoteDate(), q.getTotal(),
                        q.getCreatedBy() == null ? "" : names.getOrDefault(q.getCreatedBy(), "")))
                .toList();
    }

    /** Default PDF sections for a brand-new statement (not saved). */
    @GetMapping("/template") @Requires(module = AppModule.QUOTATIONS, right = Right.CREATE)
    public List<QuoteSection> template(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "SOLAR") String line) {
        Quotation q = new Quotation();
        q.setBusinessLine(BusinessLine.of(line).name());
        return templates.defaults(q, me.tenantId());
    }

    @GetMapping("/{id}") @Requires(module = AppModule.QUOTATIONS, right = Right.VIEW)
    public Quotation get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { return own(id, me); }

    @PostMapping @Requires(module = AppModule.QUOTATIONS, right = Right.CREATE)
    public Quotation create(@AuthenticationPrincipal AuthUser me, @RequestBody Quotation in) {
        Long tid = me.tenantId();
        validate(in);
        Quotation q = new Quotation();
        q.setOwnerId(tid);
        q.setCreatedBy(me.userId());
        apply(q, in, tid);
        q.setEnquiryId(in.getEnquiryId() == null ? null : enquiries.find(in.getEnquiryId(), me).getId());
        if (q.getSections().isEmpty()) fillDefaults(q, tid);
        if (blank(q.getRefNo())) q.setRefNo(nextRef(tid));
        Quotation saved = repo.save(q);
        if (saved.getEnquiryId() != null) enquiries.statementCreated(me, saved.getEnquiryId(), saved);
        audit.log(me, AppModule.QUOTATIONS, "CREATE", "Quotation", saved.getId(), saved.getRefNo() + " for " + saved.getClientName());
        return saved;
    }

    @PutMapping("/{id}") @Requires(module = AppModule.QUOTATIONS, right = Right.EDIT)
    public Quotation update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody Quotation in) {
        validate(in);
        Quotation q = own(id, me);
        String ref = q.getRefNo();
        apply(q, in, me.tenantId());
        if (in.getEnquiryId() != null && !in.getEnquiryId().equals(q.getEnquiryId())) q.setEnquiryId(enquiries.find(in.getEnquiryId(), me).getId());
        else if (in.getEnquiryId() == null) q.setEnquiryId(null);
        if (blank(q.getRefNo())) q.setRefNo(blank(ref) ? nextRef(me.tenantId()) : ref);   // an empty field never wipes the number
        Quotation saved = repo.save(q);
        audit.log(me, AppModule.QUOTATIONS, "EDIT", "Quotation", saved.getId(), saved.getRefNo() + " for " + saved.getClientName());
        return saved;
    }

    @PostMapping("/{id}/clone") @Requires(module = AppModule.QUOTATIONS, right = Right.CREATE)
    public Quotation clone(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Quotation src = own(id, me);
        Quotation c = new Quotation();
        c.setOwnerId(me.tenantId());
        c.setCreatedBy(me.userId());
        apply(c, src, me.tenantId());
        c.setEnquiryId(src.getEnquiryId());
        c.setRefNo(nextRef(me.tenantId()));
        c.setQuoteDate(LocalDate.now());
        Quotation saved = repo.save(c);
        audit.log(me, AppModule.QUOTATIONS, "CREATE", "Quotation", saved.getId(), "Duplicated from " + src.getRefNo());
        return saved;
    }

    @DeleteMapping("/{id}") @Requires(module = AppModule.QUOTATIONS, right = Right.DELETE)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Quotation q = own(id, me);
        audit.log(me, AppModule.QUOTATIONS, "DELETE", "Quotation", q.getId(), q.getRefNo() + " for " + q.getClientName());
        repo.delete(q);
    }

    /** ?inline=true opens it in the browser (preview, needs PDF "view"); otherwise it is a download (needs PDF "download"). */
    @GetMapping("/{id}/pdf") @Requires(module = AppModule.PDF, right = Right.VIEW)
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                      @RequestParam(defaultValue = "false") boolean inline) {
        if (!inline && !me.can(AppModule.PDF, Right.DOWNLOAD))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to download (" + AppModule.PDF.label + ")");
        Quotation q = own(id, me);
        String name = (blank(q.getRefNo()) ? "quotation" : q.getRefNo()).replaceAll("[^A-Za-z0-9_-]", "_");
        if (!inline) audit.log(me, AppModule.PDF, "DOWNLOAD", "Quotation", q.getId(), q.getRefNo());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? "inline" : "attachment") + "; filename=\"" + name + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.build(q, me.tenantId()));
    }

    /** Loads a statement of this company. People limited to their own quotations get a 404 for anyone else's. */
    private Quotation own(Long id, AuthUser me) {
        Quotation q = repo.findByIdAndOwnerId(id, me.tenantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!me.seesAllQuotations() && !Objects.equals(q.getCreatedBy(), me.userId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (q.getSections().isEmpty()) fillDefaults(q, me.tenantId());
        return q;
    }

    private void fillDefaults(Quotation q, Long uid) {
        for (QuoteSection s : templates.defaults(q, uid)) { s.setQuotation(q); q.getSections().add(s); }
    }

    /** QTN/<year>/<n>: one more than the highest number used this year, so deleting a statement never repeats a number. */
    private String nextRef(Long uid) {
        String prefix = "QTN/" + LocalDate.now().getYear() + "/";
        int max = 0;
        for (String r : repo.findRefNos(uid, prefix + "%")) {
            try { max = Math.max(max, Integer.parseInt(r.substring(prefix.length()).trim())); } catch (NumberFormatException ignored) { }
        }
        return prefix + (max + 1);
    }

    /** Phone: digits only, at most 10. Capacity and GST cannot be negative. */
    private static void validate(Quotation in) {
        String ph = in.getPhone();
        if (ph != null && !ph.isBlank() && !ph.matches("\\d{1,10}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone must be digits only, up to 10 digits");
        if (in.getCapacityKw() != null && in.getCapacityKw().signum() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capacity cannot be negative");
        if (in.getGstPercent() != null && in.getGstPercent().signum() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GST cannot be negative");
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    /** Copies editable fields; items and sections are re-created and image keys are checked. */
    private void apply(Quotation t, Quotation in, Long uid) {
        t.setBusinessLine(BusinessLine.of(in.getBusinessLine()).name());
        t.setRefNo(in.getRefNo());
        t.setQuoteDate(in.getQuoteDate() == null ? LocalDate.now() : in.getQuoteDate());
        t.setClientName(in.getClientName());
        t.setClientAddress(in.getClientAddress());
        t.setContactPerson(in.getContactPerson());
        t.setPhone(in.getPhone());
        t.setProjectLocation(in.getProjectLocation());
        t.setCapacityKw(in.getCapacityKw());
        t.setGstPercent(in.getGstPercent());
        t.setShowItemPrices(in.isShowItemPrices());
        t.setCoverImageKey(storage.own(uid, in.getCoverImageKey()));
        t.setSignatoryName(in.getSignatoryName());
        t.setSignatoryTitle(in.getSignatoryTitle());
        t.setSignatoryPhone(in.getSignatoryPhone());
        t.setSignatoryEmail(in.getSignatoryEmail());

        t.getItems().clear();
        int n = 1;
        for (QuoteItem i : in.getItems() == null ? List.<QuoteItem>of() : in.getItems()) {
            QuoteItem x = new QuoteItem();
            x.setSno(n++);
            x.setEquipment(i.getEquipment());
            x.setSpecification(i.getSpecification());
            x.setMake(i.getMake());
            x.setUom(i.getUom());
            x.setQty(i.getQty());
            x.setUnitPrice(i.getUnitPrice());
            x.setImageKey(storage.own(uid, i.getImageKey()));
            x.setQuotation(t);
            t.getItems().add(x);
        }

        t.getSections().clear();
        int p = 0;
        for (QuoteSection s : in.getSections() == null ? List.<QuoteSection>of() : in.getSections()) {
            QuoteSection x = new QuoteSection();
            x.setPosition(p++);
            x.setKind(List.of("TEXT", "BOM", "OFFER").contains(s.getKind()) ? s.getKind() : "TEXT");
            x.setTitle(s.getTitle());
            x.setContent(s.getContent());
            x.setStyle(List.of("BULLETS", "PARAGRAPH", "TABLE").contains(s.getStyle()) ? s.getStyle() : "BULLETS");
            x.setVisible(s.isVisible());
            x.setImageColumns(Math.max(1, Math.min(3, s.getImageColumns())));
            for (String k : s.getImageKeys() == null ? List.<String>of() : s.getImageKeys()) {
                String ok = storage.own(uid, k);
                if (ok != null) x.getImageKeys().add(ok);
            }
            x.setQuotation(t);
            t.getSections().add(x);
        }
    }
}
