package com.b2borderflow.core.routing;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Строит предварительный и окончательный маршрут по правилам R-01…R-08
 * (docs/system/routing-rules.md, 6.1). Пороги читаются из {@code routing_config},
 * значения по умолчанию — раздел 4 того же документа.
 */
@Service
public class RoutingService {

    private final RoutingConfigRepository configRepository;

    public RoutingService(RoutingConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Transactional(readOnly = true)
    public RoutePlan preliminary(RoutingParameters parameters) {
        return RoutePlanner.buildRoute(parameters, thresholds(), RoutingPhase.PRELIMINARY);
    }

    @Transactional(readOnly = true)
    public RoutePlan finalRoute(RoutingParameters parameters) {
        return RoutePlanner.buildRoute(parameters, thresholds(), RoutingPhase.FINAL);
    }

    public RoutingThresholds thresholds() {
        Map<String, Object> values = configRepository.findAll().stream()
                .collect(Collectors.toMap(RoutingConfig::getConfigKey, RoutingConfig::getConfigValue));
        return RoutingThresholds.fromValues(values);
    }
}
