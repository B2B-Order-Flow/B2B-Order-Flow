package com.b2borderflow.core.security;

import com.b2borderflow.core.config.AppSecurityProperties;
import com.b2borderflow.core.error.ErrorCode;
import com.b2borderflow.core.error.ProblemWriter;
import com.b2borderflow.core.logging.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Проверка межсервисного секрета {@code X-Internal-Token}. Ядро не доверяет
 * сетевой изоляции: запрос без верного токена отклоняется даже из приватной сети
 * (api-contract.md, 4.3). Проверка выполняется до Security, поэтому корректно
 * работает и для {@code /auth/login}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    private static final Logger log = LoggerFactory.getLogger(InternalTokenFilter.class);

    private final byte[] expectedToken;
    private final ProblemWriter problemWriter;

    public InternalTokenFilter(AppSecurityProperties properties, ProblemWriter problemWriter) {
        this.expectedToken = properties.getInternalToken().getBytes(StandardCharsets.UTF_8);
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/internal/")) {
            chain.doFilter(request, response);
            return;
        }

        String actual = request.getHeader(HEADER);
        byte[] actualBytes = actual == null ? new byte[0] : actual.getBytes(StandardCharsets.UTF_8);
        if (actual == null || !MessageDigest.isEqual(expectedToken, actualBytes)) {
            log.warn("Отклонён запрос без X-Internal-Token: correlationId={}, path={}",
                    CorrelationId.current(), request.getRequestURI());
            problemWriter.write(request, response, ErrorCode.UNAUTHORIZED,
                    "Отсутствует или неверен заголовок X-Internal-Token", List.of());
            return;
        }

        chain.doFilter(request, response);
    }
}
