import re
from contextvars import ContextVar
from uuid import uuid4

from fastapi import Request

correlation_id_context: ContextVar[str] = ContextVar("correlation_id", default="")


async def correlation_middleware(request: Request, call_next):
    requested_id = request.headers.get("X-Correlation-Id", "")
    correlation_id = requested_id if re.fullmatch(r"[A-Za-z0-9-]{1,100}", requested_id) else str(uuid4())
    correlation_id_context.set(correlation_id)
    response = await call_next(request)
    response.headers["X-Correlation-Id"] = correlation_id
    return response
