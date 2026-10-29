package com.saas.quote;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface QuotationRepo extends JpaRepository<Quotation, Long> {
    List<Quotation> findByOwnerIdOrderByIdDesc(Long ownerId);
    List<Quotation> findByOwnerIdAndCreatedByOrderByIdDesc(Long ownerId, Long createdBy);
    Optional<Quotation> findByIdAndOwnerId(Long id, Long ownerId);
    @Query("select q.refNo from Quotation q where q.ownerId = :ownerId and q.refNo like :pattern")
    List<String> findRefNos(Long ownerId, String pattern);
}
