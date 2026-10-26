package com.saas.quote;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SectionRepo extends JpaRepository<FixedSection, Long> {
    List<FixedSection> findByOwnerIdOrderByPosition(Long ownerId);
}
