package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds the starting list of PDF sections from the company's standard text. */
@Service @RequiredArgsConstructor
public class TemplateService {
    private final SectionService sections;
    private final UserRepo users;

    /** q may be an empty Quotation (new statement) or an old one whose text still sits in the legacy columns. */
    public List<QuoteSection> defaults(Quotation q, Long uid) {
        BusinessLine line = BusinessLine.of(q.getBusinessLine());
        boolean genset = line == BusinessLine.GENSET;
        Map<String, FixedSection> fx = sections.mapFor(uid, line);
        AppUser u = users.findById(uid).orElseThrow();
        List<QuoteSection> out = new ArrayList<>();

        fixed(out, fx, "BRIEF", "PARAGRAPH", null);
        fixed(out, fx, "SYSTEM", "PARAGRAPH", u.getDiagramImageKey());
        fixed(out, fx, "PROFILE", "BULLETS", null);
        add(out, "TEXT", genset ? "Scope of Supply" : "Scope of Work for Proposed Project", q.getScopeText(), "BULLETS", null);
        fixed(out, fx, "CLIENT_SCOPE", "BULLETS", null);
        add(out, "TEXT", "Inclusions", q.getInclusions(), "BULLETS", null);
        add(out, "TEXT", "Exclusions", q.getExclusions(), "BULLETS", null);
        add(out, "BOM", genset ? "Bill of Material" : "Major Bill of Material for Proposed System", null, "BULLETS", null);
        add(out, "OFFER", "Commercial Offer", null, "BULLETS", null);
        if (q.getPaymentTerms() != null && !q.getPaymentTerms().isBlank())
            add(out, "TEXT", "Terms of Payments", q.getPaymentTerms(), "BULLETS", null);
        else fixed(out, fx, "PAYMENT", "BULLETS", null);
        // any other standard section of this line (for example the genset "Service Support", or ones the company added)
        for (Map.Entry<String, FixedSection> e : fx.entrySet())
            if (!CORE.contains(e.getKey())) add(out, "TEXT", e.getValue().getTitle(), e.getValue().getContent(), "BULLETS", null);
        fixed(out, fx, "TERMS", "TABLE", null);

        for (int i = 0; i < out.size(); i++) out.get(i).setPosition(i);
        return out;
    }

    private static final java.util.Set<String> CORE = java.util.Set.of("BRIEF", "SYSTEM", "PROFILE", "CLIENT_SCOPE", "PAYMENT", "TERMS");

    private void fixed(List<QuoteSection> out, Map<String, FixedSection> fx, String key, String style, String imageKey) {
        FixedSection f = fx.get(key);
        if (f != null) add(out, "TEXT", f.getTitle(), f.getContent(), style, imageKey);
    }

    private void add(List<QuoteSection> out, String kind, String title, String content, String style, String imageKey) {
        QuoteSection s = new QuoteSection();
        s.setKind(kind);
        s.setTitle(title);
        s.setContent(content);
        s.setStyle(style);
        if (imageKey != null && !imageKey.isBlank()) s.getImageKeys().add(imageKey);
        out.add(s);
    }
}
