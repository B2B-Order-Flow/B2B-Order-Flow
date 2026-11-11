package com.b2borderflow.core.integration;

import com.b2borderflow.core.config.IntegrationProperties;
import com.b2borderflow.core.request.ExternalSystem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Единая точка политики вызовов mock-сервисов: 3 попытки, задержки 200 мс и 600 мс,
 * таймаут попытки 2 с. Повторяем только 5xx, таймауты и недоступность;
 * 4xx (включая бизнес-404) не повторяем (docs/system/integrations.md, 6.1).
 */
@Component
public class ExternalCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(ExternalCallExecutor.class);

    private final RetryTemplate retryTemplate;

    public ExternalCallExecutor(IntegrationProperties properties) {
        SimpleRetryPolicy policy = new SimpleRetryPolicy(
                properties.getMaxAttempts(),
                Map.of(
                        RetryableExternalException.class, true,
                        NonRetryableExternalException.class, false));

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(properties.getRetryDelay().toMillis());
        backOff.setMultiplier(properties.getRetryMultiplier());
        backOff.setMaxInterval(properties.getRetryMaxDelay().toMillis());

        this.retryTemplate = new RetryTemplate();
        this.retryTemplate.setRetryPolicy(policy);
        this.retryTemplate.setBackOffPolicy(backOff);
    }

    /**
     * Выполняет вызов с повторами. Не бросает наружу технические сбои:
     * они становятся {@link ExternalCallResult#failure}.
     */
    public ExternalCallResult<ExternalRawResponse> execute(ExternalSystem system, String pathTemplate,
                                                           Supplier<ExternalRawResponse> call) {
        AtomicInteger attempts = new AtomicInteger();
        try {
            ExternalRawResponse response = retryTemplate.execute(context -> {
                int attempt = attempts.incrementAndGet();
                long startedAt = System.nanoTime();
                try {
                    ExternalRawResponse raw = call.get();
                    log.info("Внешний вызов: system={}, path={}, status={}, durationMs={}, attempt={}",
                            system, pathTemplate, raw.httpStatus(), elapsedMillis(startedAt), attempt);
                    return raw;
                } catch (RetryableExternalException ex) {
                    log.warn("Повтор внешнего вызова: system={}, path={}, status={}, durationMs={}, attempt={}",
                            system, pathTemplate, ex.getHttpStatus(), elapsedMillis(startedAt), attempt);
                    throw ex;
                } catch (NonRetryableExternalException ex) {
                    log.warn("Внешний вызов отклонён без повтора: system={}, path={}, status={}, durationMs={}",
                            system, pathTemplate, ex.getHttpStatus(), elapsedMillis(startedAt));
                    throw ex;
                }
            });
            return ExternalCallResult.success(response, response.httpStatus(), response.payload(), attempts.get());
        } catch (ExhaustedRetryException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            int status = statusOf(cause);
            log.error("Внешняя система недоступна: system={}, path={}, attempts={}, status={}",
                    system, pathTemplate, attempts.get(), status);
            return failure(status, attempts.get(), cause);
        } catch (RetryableExternalException ex) {
            return failure(ex.getHttpStatus(), attempts.get(), ex);
        } catch (NonRetryableExternalException ex) {
            return failure(ex.getHttpStatus(), attempts.get(), ex);
        } catch (RuntimeException ex) {
            log.error("Непредвиденный сбой внешнего вызова: system={}, path={}, attempts={}",
                    system, pathTemplate, attempts.get(), ex);
            return failure(0, attempts.get(), ex);
        }
    }

    private static ExternalCallResult<ExternalRawResponse> failure(int status, int attempts, Throwable cause) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("httpStatus", status);
        payload.put("error", sanitize(cause.getMessage()));
        return ExternalCallResult.failure(status, payload, Math.max(attempts, 1), sanitize(cause.getMessage()));
    }

    private static int statusOf(Throwable cause) {
        if (cause instanceof RetryableExternalException retryable) {
            return retryable.getHttpStatus();
        }
        if (cause instanceof NonRetryableExternalException nonRetryable) {
            return nonRetryable.getHttpStatus();
        }
        return 0;
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "внешняя система не ответила";
        }
        String singleLine = message.replace('\n', ' ').replace('\r', ' ');
        return singleLine.length() > 500 ? singleLine.substring(0, 500) : singleLine;
    }
}
