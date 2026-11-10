package com.b2borderflow.core.error;

import com.b2borderflow.core.logging.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Единая граница ошибок ядра. Ожидаемые ошибки отдаются с машинным кодом,
 * необработанные — {@code INTERNAL_ERROR} без стека и деталей запроса.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INTERNAL_DETAIL =
            "Непредвиденная ошибка. При обращении назовите correlationId";

    private final ProblemSupport problemSupport;

    public GlobalExceptionHandler(ProblemSupport problemSupport) {
        this.problemSupport = problemSupport;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        return response(ex.getCode(), ex.getMessage(), ex.getViolations(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<Violation> violations = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            violations.add(new Violation(error.getField(), "INVALID", error.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                violations.add(new Violation(error.getObjectName(), "INVALID", error.getDefaultMessage())));
        return response(ErrorCode.VALIDATION_FAILED, "Запрос содержит некорректные поля", violations, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException ex,
                                                                HttpServletRequest request) {
        List<Violation> violations = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String field = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                violations.add(new Violation(field, firstCode(error), error.getDefaultMessage()));
            }
        });
        ex.getCrossParameterValidationResults().forEach(error ->
                violations.add(new Violation("request", firstCode(error), error.getDefaultMessage())));
        return response(ErrorCode.VALIDATION_FAILED, "Некорректные параметры запроса", violations, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        List<Violation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new Violation(
                        violation.getPropertyPath().toString(),
                        "INVALID",
                        violation.getMessage()))
                .toList();
        return response(ErrorCode.VALIDATION_FAILED, "Запрос содержит некорректные поля", violations, request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ProblemDetail> handleMalformed(Exception ex, HttpServletRequest request) {
        return response(ErrorCode.MALFORMED_REQUEST, "Некорректный синтаксис запроса", List.of(), request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ProblemDetail> handleNoResource(Exception ex, HttpServletRequest request) {
        return response(ErrorCode.NOT_FOUND, "Запрошенный ресурс не найден", List.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                HttpServletRequest request) {
        return response(ErrorCode.METHOD_NOT_ALLOWED,
                "Для этого ресурса метод " + ex.getMethod() + " не поддерживается", List.of(), request);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
    public ResponseEntity<ProblemDetail> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        return response(ErrorCode.VERSION_CONFLICT,
                "Объект изменён другим действием. Перечитайте состояние и повторите", List.of(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        log.warn("Нарушение ограничения БД, correlationId={}", CorrelationId.current(), ex);
        return response(ErrorCode.CONFLICT, "Действие конфликтует с текущими данными", List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return response(ErrorCode.ACCESS_DENIED, "Действие не разрешено", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Необработанная ошибка, correlationId={}, path={}",
                CorrelationId.current(), request.getRequestURI(), ex);
        return response(ErrorCode.INTERNAL_ERROR, INTERNAL_DETAIL, List.of(), request);
    }

    private ResponseEntity<ProblemDetail> response(ErrorCode code, String detail,
                                                   List<Violation> violations, HttpServletRequest request) {
        ProblemDetail problem = problemSupport.create(code, detail, request.getRequestURI(), violations);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(code.getStatus().value()));
    }

    private static String firstCode(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        return codes != null && codes.length > 0 ? codes[0] : "INVALID";
    }
}
