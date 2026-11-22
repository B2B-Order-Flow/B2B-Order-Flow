package com.b2borderflow.core.user;

import com.b2borderflow.core.domain.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByLoginIgnoreCase(String login);

    Optional<AppUser> findByLoginIgnoreCaseAndActiveTrue(String login);

    boolean existsByLoginIgnoreCase(String login);

    /** Активные сотрудники оператора с ролью — источник GET /users (api-contract.md, 6.1). */
    @Query("""
            select u from AppUser u
            where u.active = true
              and exists (select 1 from OperatorRole r
                          where r.id.userId = u.id and r.id.role = :role)
            """)
    Page<AppUser> findActiveByOperatorRole(@Param("role") Role role, Pageable pageable);
}
