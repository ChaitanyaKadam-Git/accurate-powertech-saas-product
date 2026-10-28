package com.saas.quote;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QuotationRepo extends JpaRepository<Quotation, Long> {
    List<Quotation> findByOwnerIdOrderByIdDesc(Long ownerId);
    List<Quotation> findByOwnerIdAndCreatedByOrderByIdDesc(Long ownerId, Long createdBy);
    Optional<Quotation> findByIdAndOwnerId(Long id, Long ownerId);
    long countByOwnerId(Long ownerId);
}
