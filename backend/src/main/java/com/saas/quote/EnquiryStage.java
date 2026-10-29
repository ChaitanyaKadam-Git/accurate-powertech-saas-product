package com.saas.quote;

/** The customer flow, in order. LOST ends an enquiry without an order. */
public enum EnquiryStage {
    NEW("New enquiry"),
    SITE_VISIT("Site visit / survey"),
    QUOTED("Quotation sent"),
    NEGOTIATION("Negotiation"),
    ORDER("Order received"),
    DELIVERED("Delivered"),
    INSTALLED("Installed"),
    COMMISSIONED("Commissioned"),
    LOST("Lost");

    public final String label;
    EnquiryStage(String label) { this.label = label; }

    /** Still being sold (not yet won or lost). */
    public boolean open() { return ordinal() < ORDER.ordinal(); }
    public boolean won() { return this != LOST && ordinal() >= ORDER.ordinal(); }

    public static EnquiryStage of(String s) {
        try { return s == null ? null : valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return null; }
    }
}
