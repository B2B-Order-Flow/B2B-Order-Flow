from typing import Any

from fastapi import Request
from fastapi.responses import JSONResponse


def problem(status: int, code: str, detail: str, correlation_id: str, retryable: bool = False) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        media_type="application/problem+json",
        content={
            "type": f"https://b2b-order-flow/errors/{code.lower()}",
            "title": detail,
            "status": status,
            "detail": detail,
            "code": code,
            "retryable": retryable,
            "correlationId": correlation_id,
        },
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


async def validation_error_handler(request: Request, exc: Exception) -> JSONResponse:
    correlation_id = request.headers.get("X-Correlation-Id", "unknown")
    return problem(422, "VALIDATION_ERROR", str(exc), correlation_id, False)

