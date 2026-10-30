package com.saas.quote;

/** Kinds of document a statement can be; each has its own numbering prefix and templates. */
public enum DocType {
    QUOTATION("Quotation", "QTN"),
    PROFORMA("Proforma invoice", "PI"),
    INVOICE("Tax invoice", "INV");

    public final String label, prefix;
    DocType(String label, String prefix) { this.label = label; this.prefix = prefix; }

    /** Unknown or empty values fall back to QUOTATION. */
    public static DocType of(String s) {
        if (s == null || s.isBlank()) return QUOTATION;
        try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return QUOTATION; }
    }
}
