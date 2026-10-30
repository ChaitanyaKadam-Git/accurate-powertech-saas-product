package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Creates each company's starting templates and turns a template into a document's sections. */
@Service @RequiredArgsConstructor
public class DocTemplateService {
    private final DocTemplateRepo repo;
    private final TemplateService standardText;
    private final SectionService sections;

    /**
     * The first time templates are used, every type x line gets a default template. The quotation ones are built from the
     * company's current standard text, so nothing written there is lost.
     */
    @Transactional
    public void ensureDefaults(Long tenantId) {
        for (DocType t : DocType.values())
            for (BusinessLine l : BusinessLine.values())
                if (repo.findByTenantIdAndDocTypeAndBusinessLine(tenantId, t.name(), l.name()).isEmpty()) repo.save(starting(tenantId, t, l));
    }

    /** The default (or first active) template of a type and line. */
    @Transactional
    public DocTemplate defaultFor(Long tenantId, DocType type, BusinessLine line) {
        ensureDefaults(tenantId);
        List<DocTemplate> list = repo.findByTenantIdAndDocTypeAndBusinessLine(tenantId, type.name(), line.name());
        return list.stream().filter(x -> x.isDefault() && x.isActive()).findFirst()
                .orElse(list.stream().filter(DocTemplate::isActive).findFirst().orElse(list.get(0)));
    }

    /** New QuoteSections for a document, copied from the template. */
    public List<QuoteSection> sectionsOf(DocTemplate t) {
        List<QuoteSection> out = new ArrayList<>();
        int p = 0;
        for (TemplateSection s : t.getSections()) {
            QuoteSection q = new QuoteSection();
            q.setPosition(p++); q.setKind(s.getKind()); q.setTitle(s.getTitle()); q.setContent(s.getContent());
            q.setStyle(s.getStyle()); q.setVisible(s.isVisible());
            out.add(q);
        }
        return out;
    }

    private DocTemplate starting(Long tenantId, DocType type, BusinessLine line) {
        DocTemplate t = new DocTemplate();
        t.setTenantId(tenantId); t.setDocType(type.name()); t.setBusinessLine(line.name());
        t.setDefault(true); t.setActive(true); t.setCreatedAt(Instant.now()); t.setUpdatedAt(Instant.now());
        if (type == DocType.QUOTATION) {
            t.setName(line.label + " - standard quotation");
            t.setDescription("Created from the standard text under Settings.");
            Quotation blank = new Quotation();
            blank.setBusinessLine(line.name());
            for (QuoteSection s : standardText.defaults(blank, tenantId)) add(t, s.getKind(), s.getTitle(), s.getContent(), s.getStyle());
            return t;
        }
        boolean proforma = type == DocType.PROFORMA;
        t.setName(line.label + " - " + type.label.toLowerCase());
        t.setShowCover(false); t.setShowLetter(false);
        Map<String, FixedSection> fx = sections.mapFor(tenantId, line);
        add(t, "BOM", "Items", null, "BULLETS");
        add(t, "OFFER", "Amount", null, "BULLETS");
        FixedSection pay = fx.get("PAYMENT");
        add(t, "TEXT", "Terms of payment", pay == null ? "As agreed in the purchase order." : pay.getContent(), "BULLETS");
        add(t, "TEXT", "Bank details", "Bank name | [your bank]\nAccount name | [account name]\nAccount number | [account number]\nIFSC | [IFSC code]", "TABLE");
        add(t, "TEXT", "Declaration", proforma
                ? "This is a proforma invoice for advance payment and is not a tax invoice. A tax invoice will be issued on dispatch."
                : "We declare that this invoice shows the actual price of the goods described and that all particulars are true and correct.", "PARAGRAPH");
        if (!proforma) add(t, "TEXT", "Terms", "Interest at 18% per annum will be charged on payments delayed beyond the due date.\nSubject to Pune jurisdiction.", "BULLETS");
        return t;
    }

    private static void add(DocTemplate t, String kind, String title, String content, String style) {
        TemplateSection s = new TemplateSection();
        s.setPosition(t.getSections().size()); s.setKind(kind); s.setTitle(title); s.setContent(content);
        s.setStyle(style == null ? "BULLETS" : style); s.setVisible(true); s.setTemplate(t);
        t.getSections().add(s);
    }
}
