package com.b2borderflow.core.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Проставляет сквозной {@code X-Correlation-Id}: берёт из запроса или генерирует,
 * кладёт в MDC, возвращает в ответе и передаёт дальше по цепочке.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = CorrelationId.normalize(request.getHeader(HEADER));
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        request.setAttribute(CorrelationId.MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
