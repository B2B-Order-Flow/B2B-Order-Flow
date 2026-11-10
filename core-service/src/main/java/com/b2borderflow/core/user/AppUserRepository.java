package com.b2borderflow.core.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByLoginIgnoreCase(String login);

    Optional<AppUser> findByLoginIgnoreCaseAndActiveTrue(String login);

    boolean existsByLoginIgnoreCase(String login);
}
