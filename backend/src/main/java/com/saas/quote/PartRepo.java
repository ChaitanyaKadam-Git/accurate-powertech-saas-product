package com.saas.quote;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface PartRepo extends JpaRepository<Part, Long> {
    List<Part> findByTenantIdOrderByCategoryAscNameAsc(Long tenantId);
    Optional<Part> findByIdAndTenantId(Long id, Long tenantId);

    @Query("select p from Part p where p.tenantId = :tenantId and lower(p.name) = lower(:name) and lower(coalesce(p.make, '')) = lower(coalesce(:make, ''))")
    List<Part> findSame(Long tenantId, String name, String make);

    /** [part id, number of products using it] for one company. */
    @Query("select pp.part.id, count(distinct pp.product.id) from ProductPart pp where pp.product.tenantId = :tenantId group by pp.part.id")
    List<Object[]> usage(Long tenantId);

    @Query("select distinct pp.product.name from ProductPart pp where pp.part.id = :partId order by pp.product.name")
    List<String> usedBy(Long partId);
}
