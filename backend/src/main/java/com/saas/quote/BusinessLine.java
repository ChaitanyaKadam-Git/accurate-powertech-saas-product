package com.saas.quote;

import java.math.BigDecimal;

/**
 * What a statement is for. Each line has its own standard text (Settings), capacity unit, default GST and PDF wording.
 * Stored as the enum name on Quotation and FixedSection; add new lines here (rental, AMC, panels, ...).
 */
public enum BusinessLine {
    SOLAR("Solar EPC", "kWp", new BigDecimal("8.9"), new BigDecimal("30")),
    GENSET("Genset", "kVA", new BigDecimal("18"), new BigDecimal("62.5"));

    public final String label, unit;
    public final BigDecimal defaultGst, defaultCapacity;

    BusinessLine(String label, String unit, BigDecimal defaultGst, BigDecimal defaultCapacity) {
        this.label = label; this.unit = unit; this.defaultGst = defaultGst; this.defaultCapacity = defaultCapacity;
    }

    /** Unknown or empty values fall back to SOLAR, the line every statement had before lines existed. */
    public static BusinessLine of(String s) {
        if (s == null || s.isBlank()) return SOLAR;
        try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return SOLAR; }
    }
}
