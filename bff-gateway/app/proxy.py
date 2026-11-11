from typing import Any

from fastapi import Request, Response

from .core_client import CoreClient
from .errors import problem
from .headers import correlation_id_context


def core_path(path: str) -> str:
    if path.startswith("/api/v1/"):
        return "/internal/v1/" + path.removeprefix("/api/v1/")
    return path


async def proxy(
    request: Request,
    client: CoreClient,
    method: str,
    path: str,
    body: Any = None,
) -> Response:
    headers = {
        name: request.headers[name]
        for name in ("Authorization", "X-Active-Role", "Idempotency-Key")
        if request.headers.get(name)
    }
    upstream = await client.request(
        method,
        core_path(path),
        correlation_id_context.get(),
        headers=headers,
        params=request.query_params,
        json=body,
    )
    if 500 <= upstream.status_code < 600 and upstream.status_code not in (503, 504):
        return problem(500, "INTERNAL_ERROR", "Внутренняя ошибка сервиса", correlation_id_context.get(), False)
    content_type = upstream.headers.get("content-type", "application/json")
    response_headers = {
        name: upstream.headers[name] for name in ("location", "x-correlation-id") if name in upstream.headers
    }
    response_headers.setdefault("X-Correlation-Id", correlation_id_context.get())
    return Response(
        content=upstream.content,
        status_code=upstream.status_code,
        media_type=content_type.split(";")[0],
        headers=response_headers,
    )
