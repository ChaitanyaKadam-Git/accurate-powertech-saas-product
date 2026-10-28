package com.saas.quote;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoleRepo extends JpaRepository<Role, Long> {
    List<Role> findByTenantIdOrderByName(Long tenantId);
    Optional<Role> findByIdAndTenantId(Long id, Long tenantId);
    Optional<Role> findByTenantIdAndNameIgnoreCase(Long tenantId, String name);
    Optional<Role> findByTenantIdAndAdminRoleTrue(Long tenantId);
}
