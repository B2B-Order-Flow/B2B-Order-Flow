package com.b2borderflow.core.routing;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutingConfigRepository extends JpaRepository<RoutingConfig, String> {

    Optional<RoutingConfig> findByConfigKey(String configKey);
}
