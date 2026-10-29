package com.saas.quote;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface EnquiryRepo extends JpaRepository<Enquiry, Long> {
    List<Enquiry> findByTenantIdOrderByIdDesc(Long tenantId);
    List<Enquiry> findByTenantIdAndCustomerIdOrderByIdDesc(Long tenantId, Long customerId);
    Optional<Enquiry> findByIdAndTenantId(Long id, Long tenantId);
    boolean existsByTenantIdAndCustomerId(Long tenantId, Long customerId);

    @Query("select e.refNo from Enquiry e where e.tenantId = :tenantId and e.refNo like :pattern")
    List<String> findRefNos(Long tenantId, String pattern);
}
