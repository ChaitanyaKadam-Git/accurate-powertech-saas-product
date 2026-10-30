package com.saas.quote;

import java.util.List;

/**
 * The permissioned modules, named and grouped exactly like the sidebar menus (the order here is the order in the rights
 * grid). adminOnly modules can never be granted to non-admin roles. ready=false: listed so rights can be prepared, but the
 * screen does not exist yet.
 */
public enum AppModule {
    // Administration
    USERS("Users & Grant access", "Administration", true, true, Right.VIEW, Right.CREATE, Right.EDIT),
    ROLES("Roles & rights", "Administration", true, true, Right.VIEW, Right.CREATE, Right.EDIT, Right.DELETE),
    ACTIVITY("Activity log", "Administration", true, true, Right.VIEW, Right.DOWNLOAD),
    // Workspace
    ENQUIRIES("Enquiries", "Workspace", false, true, Right.VIEW, Right.CREATE, Right.EDIT, Right.DELETE),
    QUOTATIONS("Statements", "Workspace", false, true, Right.VIEW, Right.CREATE, Right.EDIT, Right.DELETE),
    PDF("Statement PDF", "Workspace", false, true, Right.VIEW, Right.DOWNLOAD),
    REVISIONS("Revision history", "Workspace", false, false, Right.VIEW, Right.DOWNLOAD),
    // Masters
    PRODUCTS("Products", "Masters", false, true, Right.VIEW, Right.CREATE, Right.EDIT, Right.DELETE),
    PARTS("Parts", "Masters", false, true, Right.VIEW, Right.CREATE, Right.EDIT, Right.DELETE),
    CUSTOMERS("Customers", "Masters", false, true, Right.VIEW, Right.CREATE, Right.EDIT, Right.DELETE),
    TEMPLATE_DATA("Templates", "Masters", false, true, Right.VIEW, Right.CREATE, Right.EDIT, Right.DELETE),
    // Settings
    TEMPLATES("Company details", "Settings", false, true, Right.VIEW, Right.CREATE, Right.EDIT, Right.DELETE),
    // Communication
    EMAIL("Email", "Communication", false, false, Right.VIEW, Right.SEND),
    WHATSAPP("WhatsApp", "Communication", false, false, Right.VIEW, Right.SEND);

    public final String label, group;
    public final boolean adminOnly, ready;
    public final List<Right> rights;

    AppModule(String label, String group, boolean adminOnly, boolean ready, Right... rights) {
        this.label = label; this.group = group; this.adminOnly = adminOnly; this.ready = ready; this.rights = List.of(rights);
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
