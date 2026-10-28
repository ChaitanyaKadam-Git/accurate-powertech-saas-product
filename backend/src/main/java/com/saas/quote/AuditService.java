package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service @RequiredArgsConstructor
public class AuditService {
    private final ActivityRepo repo;

    /** Part of the caller's transaction: if the action rolls back, so does its log line. */
    public void log(AuthUser me, AppModule module, String action, String entityType, Object entityId, String detail) {
        write(me.tenantId(), me.userId(), me.name(), module.name(), action, entityType, entityId, detail);
    }

    /** Sign-in events and failures: committed on their own so they survive the request failing. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuth(Long tenantId, Long userId, String userName, String action, String detail) {
        write(tenantId, userId, userName, "AUTH", action, null, null, detail);
    }

    private void write(Long tenantId, Long userId, String userName, String module, String action, String entityType, Object entityId, String detail) {
        ActivityLog a = new ActivityLog();
        a.setTenantId(tenantId); a.setUserId(userId); a.setUserName(userName);
        a.setModule(module); a.setAction(action); a.setEntityType(entityType);
        a.setEntityId(entityId == null ? null : String.valueOf(entityId));
        a.setDetail(detail == null ? null : detail.substring(0, Math.min(detail.length(), 990)));
        a.setIp(ClientIp.current()); a.setAt(Instant.now());
        repo.save(a);
    }
}
