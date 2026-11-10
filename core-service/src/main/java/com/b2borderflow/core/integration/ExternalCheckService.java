package com.b2borderflow.core.integration;

import com.b2borderflow.core.logging.MdcContext;
import com.b2borderflow.core.request.ExternalSystem;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Параллельный опрос трёх mock-систем с общим бюджетом около 7 секунд
 * (integrations.md, 6.2). Каждый клиент уже содержит политику повторов.
 */
@Service
public class ExternalCheckService {

    private static final Logger log = LoggerFactory.getLogger(ExternalCheckService.class);

    private final CrmClient crmClient;
    private final BillingClient billingClient;
    private final RegistryClient registryClient;
    private final ExecutorService externalCallExecutor;

    public ExternalCheckService(CrmClient crmClient,
                                BillingClient billingClient,
                                RegistryClient registryClient,
                                @org.springframework.beans.factory.annotation.Qualifier("externalTaskExecutor")
                                ExecutorService externalCallExecutor) {
        this.crmClient = crmClient;
        this.billingClient = billingClient;
        this.registryClient = registryClient;
        this.externalCallExecutor = externalCallExecutor;
    }

    public Map<ExternalSystem, ExternalCallResult<?>> checkAll(String inn) {
        return runChecks(inn, EnumSet.allOf(ExternalSystem.class));
    }

    /**
     * Повторная проверка запрашивает только недостающие системы, поэтому уже
     * полученные данные не перезатираются (integrations.md, 6.3).
     */
    public Map<ExternalSystem, ExternalCallResult<?>> checkMissing(String inn, Set<ExternalSystem> alreadySuccessful) {
        EnumSet<ExternalSystem> missing = EnumSet.allOf(ExternalSystem.class);
        missing.removeAll(alreadySuccessful);
        return runChecks(inn, missing);
    }

    private Map<ExternalSystem, ExternalCallResult<?>> runChecks(String inn, Set<ExternalSystem> systems) {
        EnumMap<ExternalSystem, ExternalCallResult<?>> results = new EnumMap<>(ExternalSystem.class);
        EnumMap<ExternalSystem, CompletableFuture<ExternalCallResult<?>>> futures =
                new EnumMap<>(ExternalSystem.class);

        for (ExternalSystem system : systems) {
            try {
                futures.put(system, CompletableFuture.supplyAsync(
                        MdcContext.wrap(() -> call(system, inn)), externalCallExecutor));
            } catch (RuntimeException ex) {
                results.put(system, failure(system, ex));
            }
        }

        futures.forEach((system, future) -> {
            try {
                results.put(system, future.join());
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                results.put(system, failure(system, cause));
            } catch (RuntimeException ex) {
                results.put(system, failure(system, ex));
            }
        });

        return results;
    }

    private ExternalCallResult<?> call(ExternalSystem system, String inn) {
        return switch (system) {
            case CRM -> crmClient.fetch(inn);
            case BILLING -> billingClient.fetch(inn);
            case REGISTRY -> registryClient.fetch(inn);
        };
    }

    private static ExternalCallResult<?> failure(ExternalSystem system, Throwable cause) {
        String message = cause.getMessage() == null ? "внешняя система не ответила" : cause.getMessage();
        log.error("Не удалось выполнить проверку внешней системы {}", system, cause);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("httpStatus", 0);
        payload.put("error", message);
        return ExternalCallResult.failure(0, payload, 1, message);
    }
}
