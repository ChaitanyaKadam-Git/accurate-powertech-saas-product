package com.saas.quote;

import java.util.Set;

/** The signed-in person for one request. tenantId is the company (owner) id used for all data; userId is the actual person. */
public record AuthUser(Long userId, Long tenantId, String name, boolean admin, boolean mustChangePassword,
                       Set<String> perms, boolean seesAllQuotations, Long sessionId) {
    public boolean can(AppModule m, Right r) { return admin || perms.contains(AppModule.key(m, r)); }
    public boolean canAny(AppModule m, Right... rs) { for (Right r : rs) if (can(m, r)) return true; return false; }
}
