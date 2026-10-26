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
import java.util.List;

@RestController @RequestMapping("/api/quotations") @RequiredArgsConstructor @Transactional
public class QuotationController {
    private final QuotationRepo repo;
    private final PdfService pdf;
    private final StorageService storage;
    private final TemplateService templates;

    record Summary(Long id, String refNo, String clientName, String projectLocation, LocalDate quoteDate, BigDecimal total) {}

    @GetMapping
    public List<Summary> list(@AuthenticationPrincipal Long uid) {
        return repo.findByOwnerIdOrderByIdDesc(uid).stream()
                .map(q -> new Summary(q.getId(), q.getRefNo(), q.getClientName(), q.getProjectLocation(), q.getQuoteDate(), q.getTotal()))
                .toList();
    }

    /** Default PDF sections for a brand-new statement (not saved). */
    @GetMapping("/template")
    public List<QuoteSection> template(@AuthenticationPrincipal Long uid) { return templates.defaults(new Quotation(), uid); }

    @GetMapping("/{id}")
    public Quotation get(@AuthenticationPrincipal Long uid, @PathVariable Long id) { return own(id, uid); }

    @PostMapping
    public Quotation create(@AuthenticationPrincipal Long uid, @RequestBody Quotation in) {
        Quotation q = new Quotation();
        q.setOwnerId(uid);
        apply(q, in, uid);
        if (q.getSections().isEmpty()) fillDefaults(q, uid);
        if (blank(q.getRefNo())) q.setRefNo(nextRef(uid));
        return repo.save(q);
    }

    @PutMapping("/{id}")
    public Quotation update(@AuthenticationPrincipal Long uid, @PathVariable Long id, @RequestBody Quotation in) {
        Quotation q = own(id, uid);
        apply(q, in, uid);
        return repo.save(q);
    }

    @PostMapping("/{id}/clone")
    public Quotation clone(@AuthenticationPrincipal Long uid, @PathVariable Long id) {
        Quotation c = new Quotation();
        c.setOwnerId(uid);
        apply(c, own(id, uid), uid);
        c.setRefNo(nextRef(uid));
        c.setQuoteDate(LocalDate.now());
        return repo.save(c);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal Long uid, @PathVariable Long id) { repo.delete(own(id, uid)); }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal Long uid, @PathVariable Long id) {
        Quotation q = own(id, uid);
        String name = (blank(q.getRefNo()) ? "quotation" : q.getRefNo()).replaceAll("[^A-Za-z0-9_-]", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.build(q, uid));
    }

    /** Loads a statement; statements saved before sections existed get their sections built from the old fields. */
    private Quotation own(Long id, Long uid) {
        Quotation q = repo.findByIdAndOwnerId(id, uid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (q.getSections().isEmpty()) fillDefaults(q, uid);
        return q;
    }

    private void fillDefaults(Quotation q, Long uid) {
        for (QuoteSection s : templates.defaults(q, uid)) { s.setQuotation(q); q.getSections().add(s); }
    }

    private String nextRef(Long uid) { return "QTN/" + LocalDate.now().getYear() + "/" + (repo.countByOwnerId(uid) + 1); }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    /** Copies editable fields; items and sections are re-created and image keys are checked. */
    private void apply(Quotation t, Quotation in, Long uid) {
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
        for (QuoteItem i : in.getItems()) {
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
        for (QuoteSection s : in.getSections()) {
            QuoteSection x = new QuoteSection();
            x.setPosition(p++);
            x.setKind(List.of("TEXT", "BOM", "OFFER").contains(s.getKind()) ? s.getKind() : "TEXT");
            x.setTitle(s.getTitle());
            x.setContent(s.getContent());
            x.setStyle(List.of("BULLETS", "PARAGRAPH", "TABLE").contains(s.getStyle()) ? s.getStyle() : "BULLETS");
            x.setVisible(s.isVisible());
            x.setImageColumns(Math.max(1, Math.min(3, s.getImageColumns())));
            for (String k : s.getImageKeys()) {
                String ok = storage.own(uid, k);
                if (ok != null) x.getImageKeys().add(ok);
            }
            x.setQuotation(t);
            t.getSections().add(x);
        }
    }
}
