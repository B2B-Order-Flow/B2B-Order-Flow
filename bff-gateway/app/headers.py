from contextvars import ContextVar
from uuid import uuid4

from fastapi import Request

correlation_id_context: ContextVar[str] = ContextVar("correlation_id", default="")


async def correlation_middleware(request: Request, call_next):
    correlation_id = request.headers.get("X-Correlation-Id") or str(uuid4())
    correlation_id_context.set(correlation_id)
    response = await call_next(request)
    response.headers["X-Correlation-Id"] = correlation_id
    return response

