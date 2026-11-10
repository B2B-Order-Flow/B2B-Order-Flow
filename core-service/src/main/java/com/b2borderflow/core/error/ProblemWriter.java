package com.b2borderflow.core.error;

import com.b2borderflow.core.logging.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Записывает problem+json из фильтров (вне {@code @RestControllerAdvice}):
 * отсутствие межсервисного токена и ошибки Security. Ошибка логируется здесь
 * один раз, на границе сервиса.
 */
@Component
public class ProblemWriter {

    private static final Logger log = LoggerFactory.getLogger(ProblemWriter.class);

    private final ObjectMapper objectMapper;
    private final ProblemSupport problemSupport;

    public ProblemWriter(ObjectMapper objectMapper, ProblemSupport problemSupport) {
        this.objectMapper = objectMapper;
        this.problemSupport = problemSupport;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      ErrorCode code, String detail, List<Violation> violations) {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            objectMapper.writeValue(
                    response.getOutputStream(),
                    problemSupport.create(code, detail, request.getRequestURI(), violations));
        } catch (IOException | RuntimeException ex) {
            log.error("Не удалось записать тело ошибки {} для {}", code, request.getRequestURI(), ex);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            try {
                response.getOutputStream().write(("{\"code\":\"" + code.name()
                        + "\",\"correlationId\":\"" + response.getHeader(CorrelationIdFilter.HEADER)
                        + "\",\"retryable\":false}").getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // ответ уже недоступен; логировать повторно не нужно
            }
        }
    }
}
