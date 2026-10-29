package com.saas.quote;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Page order: cover (with image) -> table of contents -> cover letter -> chapters A..K.
 * Every page after the cover has the company name (left) and logo (right) at the top.
 * The document is rendered twice: pass 1 records the page each chapter starts on, pass 2 prints them in the contents page.
 */
@Service @RequiredArgsConstructor
public class PdfService {
    private final StorageService storage;
    private final UserRepo users;

    private static final Color NAVY = new Color(0x0B, 0x2A, 0x6B), BLUE = new Color(0x2F, 0x55, 0x97),
            BAR = new Color(0xC6, 0xD9, 0xF1), RED = new Color(0xB0, 0x10, 0x10), GREEN = new Color(0x4C, 0x8A, 0x10);
    private static final Font TITLE = f(Font.BOLD, 16, NAVY), RED_B = f(Font.BOLD, 12, RED), H = f(Font.BOLD, 11, Color.BLACK),
            B = f(Font.NORMAL, 10, Color.BLACK), BB = f(Font.BOLD, 10, Color.BLACK), TH = f(Font.BOLD, 9, Color.BLACK),
            SM = f(Font.NORMAL, 8.5f, Color.BLACK), HEAD = f(Font.BOLDITALIC, 15, BLUE);
    private static final NumberFormat INR = NumberFormat.getNumberInstance(new Locale("en", "IN"));
    static { INR.setMinimumFractionDigits(2); INR.setMaximumFractionDigits(2); }

    private static Font f(int style, float size, Color c) { return FontFactory.getFont(FontFactory.HELVETICA, size, style, c); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String s(String v) { return v == null ? "" : v; }
    private static String pick(String a, String b) { return blank(a) ? s(b) : a; }
    private static String rs(BigDecimal v) { return "Rs. " + INR.format(v == null ? BigDecimal.ZERO : v); }

    public byte[] build(Quotation q, Long uid) {
        try {
            AppUser u = users.findById(uid).orElseThrow();
            List<QuoteSection> secs = active(q);
            Map<String, Integer> pages = new LinkedHashMap<>();
            render(q, u, secs, null, pages);                                   // pass 1: find the chapter pages
            return render(q, u, secs, pages, new LinkedHashMap<>());           // pass 2: print them
        } catch (Exception e) {
            throw new IllegalStateException("PDF generation failed", e);
        }
    }

    /** Visible sections in order; text sections with no text and no photos are skipped. */
    private List<QuoteSection> active(Quotation q) {
        List<QuoteSection> l = new ArrayList<>();
        for (QuoteSection s : q.getSections()) {
            if (!s.isVisible()) continue;
            if ("TEXT".equals(s.getKind()) && blank(s.getContent()) && s.getImageKeys().isEmpty()) continue;
            l.add(s);
        }
        l.sort(Comparator.comparingInt(QuoteSection::getPosition));
        return l;
    }

    private static String letter(int i) { return i < 26 ? String.valueOf((char) ('A' + i)) : String.valueOf(i + 1); }
    private static String title(QuoteSection s) { return blank(s.getTitle()) ? "Section" : s.getTitle(); }

    private byte[] render(Quotation q, AppUser u, List<QuoteSection> secs, Map<String, Integer> known, Map<String, Integer> collect) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document d = new Document(PageSize.A4, 40, 40, 95, 55);
        PdfWriter w = PdfWriter.getInstance(d, out);
        w.setPageEvent(new PageDecor(u.getCompanyName(), loadLogo(u)));
        d.open();

        cover(d, q, u);
        d.newPage();
        toc(d, secs, known);
        d.newPage();
        letter(d, q, u);
        d.newPage();

        for (int i = 0; i < secs.size(); i++) {
            QuoteSection s = secs.get(i);
            if ("BOM".equals(s.getKind())) d.newPage();
            bar(d, letter(i) + ". " + title(s));
            collect.put(String.valueOf(i), w.getPageNumber());
            switch (s.getKind()) {
                case "BOM" -> bom(d, q);
                case "OFFER" -> offer(d, q);
                default -> {
                    String text = fill(s.getContent(), q);
                    if ("PARAGRAPH".equals(s.getStyle())) paragraphs(d, text);
                    else if ("TABLE".equals(s.getStyle())) termsTable(d, text);
                    else bullets(d, text);
                }
            }
            images(d, s.getImageKeys(), s.getImageColumns());
        }

        d.add(new Paragraph("\nThanks & Regards,\n" + u.getCompanyName(), BB));
        d.close();
        return out.toByteArray();
    }

    // ---------- pages ----------
    private void cover(Document d, Quotation q, AppUser u) throws Exception {
        if (genset(q)) {
            d.add(centered("TECHNO-COMMERCIAL OFFER", TITLE, 12));
            d.add(centered("FOR", B, 12));
            d.add(centered("SUPPLY, INSTALLATION & COMMISSIONING OF " + kw(q) + " KVA DIESEL GENERATOR SET, " + s(q.getProjectLocation()).toUpperCase(), RED_B, 14));
        } else {
            d.add(centered("TECHNO-COMMERCIAL PROPOSAL FOR ENGINEERING, PROCUREMENT AND CONSTRUCTION", TITLE, 12));
            d.add(centered("FOR", B, 12));
            d.add(centered(kw(q) + " KWp DC GRID CONNECTED ROOFTOP SOLAR PHOTOVOLTAIC POWER PROJECT, " + s(q.getProjectLocation()).toUpperCase(), RED_B, 14));
        }
        d.add(centered("PREPARED FOR", B, 4));
        d.add(centered(s(q.getClientName()), BB, 4));
        d.add(centered(s(q.getClientAddress()), B, 14));
        image(d, pick(q.getCoverImageKey(), u.getCoverImageKey()), 470, 300);
        d.add(centered("\nPREPARED BY", BB, 4));
        d.add(centered(u.getCompanyName(), f(Font.BOLD, 15, GREEN), 4));
        if (!blank(u.getAddress())) d.add(centered(u.getAddress(), f(Font.NORMAL, 10, GREEN), 0));
    }

    private void toc(Document d, List<QuoteSection> secs, Map<String, Integer> known) throws Exception {
        d.add(centered("TABLE OF CONTENTS", f(Font.BOLD, 13, BLUE), 16));
        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{10, 75, 15});
        for (String h : new String[]{"Sr. No.", "Chapter", "Page No."}) {
            PdfPCell hc = cell(h, TH, Element.ALIGN_CENTER);
            hc.setBackgroundColor(BAR);
            t.addCell(hc);
        }
        for (int i = 0; i < secs.size(); i++) {
            Integer p = known == null ? null : known.get(String.valueOf(i));
            t.addCell(cell(letter(i), B, Element.ALIGN_CENTER));
            t.addCell(cell(title(secs.get(i)), B, Element.ALIGN_LEFT));
            t.addCell(cell(p == null ? "-" : String.valueOf(p), B, Element.ALIGN_CENTER));
        }
        d.add(t);
    }

    private void letter(Document d, Quotation q, AppUser u) throws Exception {
        LocalDate date = q.getQuoteDate() == null ? LocalDate.now() : q.getQuoteDate();
        d.add(new Paragraph("Ref: " + s(q.getRefNo()) + "                                                        Date: "
                + date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), BB));
        d.add(new Paragraph("\nTo,\n" + s(q.getClientName()) + "\n" + s(q.getClientAddress()), B));
        if (!blank(q.getContactPerson()) || !blank(q.getPhone()))
            d.add(new Paragraph("\nMr./Ms. " + s(q.getContactPerson()) + "\nPhone: " + s(q.getPhone()), B));
        if (genset(q)) {
            d.add(new Paragraph("\nSUBJECT: Offer for Supply, Installation & Commissioning of " + kw(q) + " kVA Diesel Generator Set at "
                    + s(q.getProjectLocation()) + ".", BB));
            d.add(new Paragraph("\nDear Sir,\n\nWe thank you for your enquiry and the opportunity to submit our offer for a " + kw(q)
                    + " kVA diesel generator set for your requirement at " + s(q.getProjectLocation()) + ".\n\n"
                    + "Please find enclosed our techno-commercial offer, covering the genset specification, bill of material, commercial terms and our scope of service.\n\n"
                    + "We look forward to your valued order and assure you of our best services at all times.\n\n"
                    + "Thanking you,\nYours faithfully,\n\n", B));
        } else {
            d.add(new Paragraph("\nSUBJECT: Turnkey EPC Proposal for Developing Your Proposed " + kw(q)
                    + " KWp Grid Connected Roof Mount Solar Photovoltaic (PV) Power Project at " + s(q.getProjectLocation()) + ".", BB));
            d.add(new Paragraph("\nDear Sir,\n\nWe thank you for showing your interest and providing us an opportunity to submit our proposal for developing "
                    + kw(q) + " KWp (DC) grid tied rooftop system at " + s(q.getProjectLocation()) + ".\n\n"
                    + "As desired, we are sending across the offer for design, engineering, procurement, supply, commissioning and testing of the complete system on a total turnkey basis.\n\n"
                    + "May we look forward to your positive response in the matter and give us an opportunity to develop a solar photovoltaic power project for your esteemed organization.\n\n"
                    + "Thanking you,\nYours faithfully,\n\n", B));
        }

        StringBuilder sig = new StringBuilder();
        String name = pick(q.getSignatoryName(), u.getSignatoryName()), title = pick(q.getSignatoryTitle(), u.getSignatoryTitle());
        String phone = pick(q.getSignatoryPhone(), u.getSignatoryPhone()), mail = pick(q.getSignatoryEmail(), u.getSignatoryEmail());
        if (!blank(name)) sig.append(name.toUpperCase()).append('\n');
        if (!blank(title)) sig.append('(').append(title).append(")\n");
        sig.append(u.getCompanyName()).append('\n');
        if (!blank(u.getAddress())) sig.append(u.getAddress()).append('\n');
        if (!blank(phone)) sig.append("Contact No: ").append(phone).append('\n');
        if (!blank(mail)) sig.append("Email: ").append(mail).append('\n');
        d.add(new Paragraph(sig.toString(), BB));
    }

    // ---------- content blocks ----------
    private void bom(Document d, Quotation q) throws Exception {
        boolean prices = q.isShowItemPrices();
        boolean photos = q.getItems().stream().anyMatch(i -> !blank(i.getImageKey()));
        List<String> heads = new ArrayList<>(List.of("Sr", "Equipment", "Specification", "Make", "UOM", "Qty"));
        List<Float> widths = new ArrayList<>(List.of(5f, 20f, 26f, 12f, 7f, 7f));
        if (prices) { heads.add("Unit Price (Rs.)"); heads.add("Amount (Rs.)"); widths.add(12f); widths.add(13f); }
        if (photos) { heads.add("Photo"); widths.add(16f); }

        PdfPTable t = new PdfPTable(heads.size());
        t.setWidthPercentage(100);
        float[] wa = new float[widths.size()];
        for (int i = 0; i < wa.length; i++) wa[i] = widths.get(i);
        t.setWidths(wa);
        t.setHeaderRows(1);   // header repeats on every page
        for (String h : heads) { PdfPCell c = cell(h, TH, Element.ALIGN_CENTER); c.setBackgroundColor(BAR); t.addCell(c); }

        for (QuoteItem i : q.getItems()) {
            t.addCell(cell(String.valueOf(i.getSno()), SM, Element.ALIGN_CENTER));
            t.addCell(cell(i.getEquipment(), SM, Element.ALIGN_LEFT));
            t.addCell(cell(i.getSpecification(), SM, Element.ALIGN_LEFT));
            t.addCell(cell(i.getMake(), SM, Element.ALIGN_LEFT));
            t.addCell(cell(i.getUom(), SM, Element.ALIGN_CENTER));
            t.addCell(cell(i.getQty() == null ? "" : i.getQty().stripTrailingZeros().toPlainString(), SM, Element.ALIGN_CENTER));
            if (prices) {
                t.addCell(cell(INR.format(i.getUnitPrice() == null ? BigDecimal.ZERO : i.getUnitPrice()), SM, Element.ALIGN_RIGHT));
                t.addCell(cell(INR.format(i.getAmount()), SM, Element.ALIGN_RIGHT));
            }
            if (photos) {
                PdfPCell c = cell("", SM, Element.ALIGN_CENTER);
                try {
                    if (!blank(i.getImageKey())) {
                        Image img = Image.getInstance(storage.load(i.getImageKey()));
                        img.scaleToFit(80, 80);
                        c.addElement(img);
                    }
                } catch (Exception ignored) { /* a missing photo should not break the whole PDF */ }
                t.addCell(c);
            }
        }
        d.add(t);
    }

    private void offer(Document d, Quotation q) throws Exception {
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{40, 20, 20, 20});
        t.setKeepTogether(true);
        String gstLabel = "GST " + (q.getGstPercent() == null ? "" : q.getGstPercent().stripTrailingZeros().toPlainString()) + "%";
        for (String h : new String[]{"Work Description", "Basic Cost", gstLabel, "Total"}) {
            PdfPCell c = cell(h, TH, Element.ALIGN_CENTER);
            c.setBackgroundColor(BAR);
            t.addCell(c);
        }
        t.addCell(cell(genset(q)
                ? "Supply, installation & commissioning of " + kw(q) + " kVA diesel generator set with acoustic enclosure and control panel as per BOM"
                : "Design, engineering, supply, installation & commissioning of " + kw(q) + " KW on-grid system as per plant layout & BOM", SM, Element.ALIGN_LEFT));
        t.addCell(cell(rs(q.getSubtotal()), SM, Element.ALIGN_RIGHT));
        t.addCell(cell(rs(q.getGstAmount()), SM, Element.ALIGN_RIGHT));
        t.addCell(cell(rs(q.getTotal()), SM, Element.ALIGN_RIGHT));
        t.addCell(cell("Total", TH, Element.ALIGN_CENTER));
        t.addCell(cell(rs(q.getSubtotal()), TH, Element.ALIGN_RIGHT));
        t.addCell(cell(rs(q.getGstAmount()), TH, Element.ALIGN_RIGHT));
        t.addCell(cell(rs(q.getTotal()), TH, Element.ALIGN_RIGHT));
        d.add(t);
    }

    /** One "Topic | Details" line per row. A line without "|" becomes a full details cell. */
    private void termsTable(Document d, String text) throws Exception {
        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{6, 24, 70});
        int n = 1;
        for (String line : s(text).split("\\R")) {
            if (line.isBlank()) continue;
            String topic = "", detail = line.trim();
            int p = line.indexOf('|');
            if (p >= 0) { topic = line.substring(0, p).trim(); detail = line.substring(p + 1).trim(); }
            t.addCell(cell(String.valueOf(n++), SM, Element.ALIGN_CENTER));
            t.addCell(cell(topic, TH, Element.ALIGN_LEFT));
            t.addCell(cell(detail, SM, Element.ALIGN_LEFT));
        }
        d.add(t);
    }

    // ---------- helpers ----------
    private static boolean genset(Quotation q) { return BusinessLine.of(q.getBusinessLine()) == BusinessLine.GENSET; }

    private String kw(Quotation q) { return q.getCapacityKw() == null ? "" : q.getCapacityKw().stripTrailingZeros().toPlainString(); }

    private String fill(String text, Quotation q) {
        return s(text).replace("{capacity}", kw(q)).replace("{location}", s(q.getProjectLocation())).replace("{client}", s(q.getClientName()));
    }

    private Image loadLogo(AppUser u) {
        if (blank(u.getLogoKey())) return null;
        try {
            Image logo = Image.getInstance(storage.load(u.getLogoKey()));
            logo.scaleToFit(130, 42);
            return logo;
        } catch (Exception e) { return null; }
    }

    private void image(Document d, String key, float maxW, float maxH) {
        if (blank(key)) return;
        try {
            Image im = Image.getInstance(storage.load(key));
            im.scaleToFit(maxW, maxH);
            im.setAlignment(Image.MIDDLE);
            d.add(im);
            d.add(new Paragraph(" ", SM));
        } catch (Exception ignored) { /* skip missing image */ }
    }

    /** Photos under a section, in a grid of 1 to 3 per row. */
    private void images(Document d, List<String> keys, int cols) throws Exception {
        if (keys == null || keys.isEmpty()) return;
        cols = Math.max(1, Math.min(3, cols));
        PdfPTable t = new PdfPTable(cols);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6);
        t.setSpacingAfter(6);
        float maxW = 515f / cols - 14, maxH = cols == 1 ? 300 : 220;
        int n = 0;
        for (String k : keys) {
            PdfPCell c = new PdfPCell();
            c.setBorder(0);
            c.setPadding(4);
            try {
                Image im = Image.getInstance(storage.load(k));
                im.scaleToFit(maxW, maxH);
                im.setAlignment(Image.MIDDLE);
                c.addElement(im);
            } catch (Exception ignored) { /* skip a missing photo */ }
            t.addCell(c);
            n++;
        }
        while (n % cols != 0) { PdfPCell e = new PdfPCell(); e.setBorder(0); t.addCell(e); n++; }
        d.add(t);
    }

    private Paragraph centered(String text, Font font, float after) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingAfter(after);
        return p;
    }

    private void bar(Document d, String title) throws Exception {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(10);
        t.setSpacingAfter(6);
        PdfPCell c = new PdfPCell(new Phrase(title.toUpperCase(), H));
        c.setBackgroundColor(BAR);
        c.setBorder(0);
        c.setPadding(5);
        t.addCell(c);
        d.add(t);
    }

    private void bullets(Document d, String text) throws Exception {
        if (blank(text)) return;
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            Paragraph p = new Paragraph("\u2022  " + line.trim(), B);
            p.setIndentationLeft(12);
            p.setSpacingAfter(3);
            d.add(p);
        }
    }

    private void paragraphs(Document d, String text) throws Exception {
        if (blank(text)) return;
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            Paragraph p = new Paragraph(line.trim(), B);
            p.setAlignment(Element.ALIGN_JUSTIFIED);
            p.setSpacingAfter(6);
            d.add(p);
        }
    }

    private PdfPCell cell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(s(text), font));
        c.setPadding(4);
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return c;
    }

    /** Header (company name left, logo right) on every page except the cover, and a footer with page numbers on all pages. */
    private static class PageDecor extends PdfPageEventHelper {
        private final String company;
        private final Image logo;
        PageDecor(String company, Image logo) { this.company = company; this.logo = logo; }

        @Override
        public void onEndPage(PdfWriter w, Document d) {
            PdfContentByte cb = w.getDirectContent();
            float top = PageSize.A4.getHeight();
            if (w.getPageNumber() > 1) {
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(company, HEAD), d.left(), top - 52, 0);
                if (logo != null) {
                    try {
                        logo.setAbsolutePosition(d.right() - logo.getScaledWidth(), top - 80);
                        cb.addImage(logo);
                    } catch (Exception ignored) { }
                }
                cb.setLineWidth(0.6f);
                cb.setColorStroke(NAVY);
                cb.moveTo(d.left(), top - 86);
                cb.lineTo(d.right(), top - 86);
                cb.stroke();
            }
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase(company + "   |   Page " + w.getPageNumber(), SM), (d.right() + d.left()) / 2, 25, 0);
        }
    }
}
