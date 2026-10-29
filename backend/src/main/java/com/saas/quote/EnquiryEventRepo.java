package com.saas.quote;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnquiryEventRepo extends JpaRepository<EnquiryEvent, Long> {
    List<EnquiryEvent> findByEnquiryIdOrderByIdDesc(Long enquiryId);
    void deleteByEnquiryId(Long enquiryId);
}
