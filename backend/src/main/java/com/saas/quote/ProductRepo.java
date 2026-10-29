package com.saas.quote;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductRepo extends JpaRepository<Product, Long> {
    List<Product> findByTenantIdOrderByBusinessLineAscNameAsc(Long tenantId);
    Optional<Product> findByIdAndTenantId(Long id, Long tenantId);
    Optional<Product> findFirstByTenantIdAndBusinessLineAndNameIgnoreCase(Long tenantId, String businessLine, String name);
}
