from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .headers import correlation_id_context


def problem(
    status: int,
    code: str,
    detail: str,
    correlation_id: str,
    retryable: bool = False,
    violations: list[dict] | None = None,
) -> JSONResponse:
    slug = code.lower().replace("_", "-")
    content = {
        "type": f"https://b2b-order-flow/errors/{slug}",
        "title": detail,
        "status": status,
        "detail": detail,
        "code": code,
        "retryable": retryable,
        "correlationId": correlation_id,
    }
    if violations is not None:
        content["violations"] = violations
    return JSONResponse(
        status_code=status,
        media_type="application/problem+json",
        content=content,
    )


class CoreClientError(Exception):
    def __init__(self, status: int, code: str, detail: str, correlation_id: str, retryable: bool = True) -> None:
        self.status = status
        self.code = code
        self.detail = detail
        self.correlation_id = correlation_id
        self.retryable = retryable


async def core_client_error_handler(_: Request, exc: CoreClientError) -> JSONResponse:
    return problem(exc.status, exc.code, exc.detail, exc.correlation_id, exc.retryable)


async def validation_error_handler(_: Request, exc: RequestValidationError) -> JSONResponse:
    violations = [
        {"field": ".".join(str(part) for part in error["loc"]), "code": error["type"], "message": error["msg"]}
        for error in exc.errors()
    ]
    return problem(
        422,
        "VALIDATION_FAILED",
        "Параметры запроса не прошли проверку",
        correlation_id_context.get(),
        False,
        violations,
    )


async def internal_error_handler(_: Request, __: Exception) -> JSONResponse:
    return problem(500, "INTERNAL_ERROR", "Внутренняя ошибка сервиса", correlation_id_context.get(), False)
