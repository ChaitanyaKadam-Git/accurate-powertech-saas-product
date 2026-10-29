package com.saas.quote;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerRepo extends JpaRepository<Customer, Long> {
    List<Customer> findByTenantIdOrderByNameAsc(Long tenantId);
    Optional<Customer> findByIdAndTenantId(Long id, Long tenantId);
    Optional<Customer> findFirstByTenantIdAndNameIgnoreCase(Long tenantId, String name);
    Optional<Customer> findFirstByTenantIdAndCodeIgnoreCase(Long tenantId, String code);

    @org.springframework.data.jpa.repository.Query("select c.code from Customer c where c.tenantId = :tenantId and upper(c.code) like 'CUST-%'")
    List<String> findAutoCodes(Long tenantId);
}
