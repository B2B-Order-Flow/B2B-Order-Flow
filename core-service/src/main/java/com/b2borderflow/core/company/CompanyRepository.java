package com.b2borderflow.core.company;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByInn(String inn);

    boolean existsByInnAndStatus(String inn, CompanyStatus status);

    Page<Company> findByStatus(CompanyStatus status, Pageable pageable);
}
