package com.saas.quote;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DocTemplateRepo extends JpaRepository<DocTemplate, Long> {
    List<DocTemplate> findByTenantIdOrderByDocTypeAscBusinessLineAscNameAsc(Long tenantId);
    List<DocTemplate> findByTenantIdAndDocTypeAndBusinessLine(Long tenantId, String docType, String businessLine);
    Optional<DocTemplate> findByIdAndTenantId(Long id, Long tenantId);
    boolean existsByTenantId(Long tenantId);
}
