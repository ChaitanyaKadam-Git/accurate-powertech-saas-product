package com.saas.quote;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.Optional;

public interface SessionRepo extends JpaRepository<LoginSession, Long>, JpaSpecificationExecutor<LoginSession> {
    Optional<LoginSession> findTopByUserIdOrderBySignedInAtDesc(Long userId);
    long countByTenantIdAndSignedInAtAfter(Long tenantId, Instant since);

    @Modifying @Query("update LoginSession s set s.signedOutAt = :at, s.signOutReason = :why where s.userId = :uid and s.signedOutAt is null")
    int closeAll(Long uid, Instant at, String why);
}
