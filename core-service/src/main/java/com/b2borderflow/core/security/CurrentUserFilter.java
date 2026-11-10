package com.b2borderflow.core.security;

import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.error.ApiException;
import com.b2borderflow.core.error.ErrorCode;
import com.b2borderflow.core.error.ProblemWriter;
import com.b2borderflow.core.logging.CorrelationId;
import com.b2borderflow.core.user.AppUser;
import com.b2borderflow.core.user.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Загружает актуальные роли пользователя из БД и разрешает активную роль
 * из {@code X-Active-Role}. Фильтр ставится в цепочку Security после проверки JWT,
 * поэтому не регистрируется как отдельный servlet-фильтр.
 */
public class CurrentUserFilter extends OncePerRequestFilter {

    public static final String ACTIVE_ROLE_HEADER = "X-Active-Role";

    private static final Logger log = LoggerFactory.getLogger(CurrentUserFilter.class);

    private final AppUserRepository userRepository;
    private final CurrentUserLoader currentUserLoader;
    private final ProblemWriter problemWriter;

    public CurrentUserFilter(AppUserRepository userRepository,
                             CurrentUserLoader currentUserLoader,
                             ProblemWriter problemWriter) {
        this.userRepository = userRepository;
        this.currentUserLoader = currentUserLoader;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            chain.doFilter(request, response);
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwtAuthentication.getToken().getSubject());
        } catch (RuntimeException ex) {
            problemWriter.write(request, response, ErrorCode.UNAUTHORIZED,
                    "Некорректный идентификатор пользователя в токене", List.of());
            return;
        }

        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isActive()) {
            problemWriter.write(request, response, ErrorCode.UNAUTHORIZED,
                    "Пользователь не найден или деактивирован", List.of());
            return;
        }

        Role requestedRole;
        String rawRole = request.getHeader(ACTIVE_ROLE_HEADER);
        if (rawRole == null || rawRole.isBlank()) {
            requestedRole = null;
        } else {
            try {
                requestedRole = Role.valueOf(rawRole.trim());
            } catch (IllegalArgumentException ex) {
                problemWriter.write(request, response, ErrorCode.ACCESS_DENIED,
                        "Неизвестная активная роль: " + rawRole, List.of());
                return;
            }
        }

        UUID tokenCompanyId = parseUuid(jwtAuthentication.getToken().getClaimAsString("companyId"));

        try {
            CurrentUser currentUser = currentUserLoader.load(user, tokenCompanyId, requestedRole);
            request.setAttribute(CurrentUser.REQUEST_ATTRIBUTE, currentUser);
            MDC.put("userId", user.getId().toString());
            if (requestedRole != null) {
                MDC.put("role", requestedRole.name());
            }
            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove("userId");
                MDC.remove("role");
            }
        } catch (ApiException ex) {
            log.warn("Отказано в доступе: code={}, correlationId={}, path={}",
                    ex.getCode(), CorrelationId.current(), request.getRequestURI());
            problemWriter.write(request, response, ex.getCode(), ex.getMessage(), ex.getViolations());
        } catch (RuntimeException ex) {
            log.error("Ошибка при загрузке контекста пользователя, correlationId={}",
                    CorrelationId.current(), ex);
            problemWriter.write(request, response, ErrorCode.INTERNAL_ERROR,
                    "Непредвиденная ошибка. При обращении назовите correlationId", List.of());
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
