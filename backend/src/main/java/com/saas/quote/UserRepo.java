package com.saas.quote;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepo extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
    List<AppUser> findByTenantIdOrderByIdDesc(Long tenantId);
    Optional<AppUser> findByIdAndTenantId(Long id, Long tenantId);
    List<AppUser> findByTenantIdIsNull();
    long countByRole(Role role);
}
