package com.b2borderflow.core.error;

import java.util.List;

/**
 * Ожидаемая ошибка домена. Преобразуется общим обработчиком в
 * {@code application/problem+json} с машинным кодом.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<Violation> violations;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, List.of());
    }

    public ApiException(ErrorCode code, String detail, List<Violation> violations) {
        super(detail);
        this.code = code;
        this.violations = List.copyOf(violations);
    }

    public ErrorCode getCode() {
        return code;
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public static ApiException notFound(String detail) {
        return new ApiException(ErrorCode.NOT_FOUND, detail);
    }

    public static ApiException accessDenied(String detail) {
        return new ApiException(ErrorCode.ACCESS_DENIED, detail);
    }

    public static ApiException illegalTransition(String detail) {
        return new ApiException(ErrorCode.ILLEGAL_TRANSITION, detail);
    }

    public static ApiException versionConflict(String detail) {
        return new ApiException(ErrorCode.VERSION_CONFLICT, detail);
    }

    public static ApiException validation(String detail, List<Violation> violations) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, detail, violations);
    }
}
