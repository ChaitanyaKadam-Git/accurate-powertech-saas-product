package com.saas.quote;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuoteItemRepo extends JpaRepository<QuoteItem, Long> {
    List<QuoteItem> findByQuotationOwnerIdOrderByIdDesc(Long ownerId, Pageable page);
}
