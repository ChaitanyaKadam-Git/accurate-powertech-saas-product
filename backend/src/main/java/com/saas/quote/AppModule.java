package com.saas.quote;

import java.util.List;

/** The 12 permissioned modules. adminOnly modules can never be granted to non-admin roles. */
public enum AppModule {
    USERS("User Management & Multiple User Creation", "Users & Access", true, Right.VIEW, Right.CREATE, Right.EDIT),
    ROLES("Role & Rights Management", "Users & Access", true, Right.VIEW, Right.CREATE, Right.EDIT, Right.DELETE),
    ACTIVITY("User Activity Log", "Users & Access", true, Right.VIEW, Right.DOWNLOAD),
    CUSTOMERS("Customer Management", "Masters", false, Right.VIEW, Right.CREATE, Right.EDIT, Right.DELETE),
    PRODUCTS("Product & Category Management", "Masters", false, Right.VIEW, Right.CREATE, Right.EDIT, Right.DELETE),
    TEMPLATES("Quotation Template", "Templates", false, Right.VIEW, Right.CREATE, Right.EDIT, Right.DELETE),
    TEMPLATE_DATA("Template Data", "Templates", false, Right.VIEW, Right.CREATE, Right.EDIT, Right.DELETE),
    QUOTATIONS("Quotation Builder", "Quotations", false, Right.VIEW, Right.CREATE, Right.EDIT, Right.DELETE),
    PDF("PDF Preview & Download", "Quotations", false, Right.VIEW, Right.DOWNLOAD),
    REVISIONS("Revision History", "Quotations", false, Right.VIEW, Right.DOWNLOAD),
    EMAIL("Email Integration", "Communication", false, Right.VIEW, Right.SEND),
    WHATSAPP("WhatsApp Integration", "Communication", false, Right.VIEW, Right.SEND);

    public final String label, group;
    public final boolean adminOnly;
    public final List<Right> rights;

    AppModule(String label, String group, boolean adminOnly, Right... rights) {
        this.label = label; this.group = group; this.adminOnly = adminOnly; this.rights = List.of(rights);
    }

    public static String key(AppModule m, Right r) { return m.name() + ":" + r.name(); }

    /** True when "MODULE:RIGHT" names a real module/right pair. */
    public static boolean valid(String key) {
        if (key == null) return false;
        String[] p = key.split(":");
        if (p.length != 2) return false;
        try { return valueOf(p[0]).rights.contains(Right.valueOf(p[1])); } catch (IllegalArgumentException e) { return false; }
    }

    public static boolean isAdminOnly(String key) { return valueOf(key.split(":")[0]).adminOnly; }
}
