from typing import Any

from fastapi import Request, Response

from .core_client import CoreClient
from .headers import correlation_id_context


async def proxy(
    request: Request,
    client: CoreClient,
    method: str,
    path: str,
    body: Any = None,
) -> Response:
    active_role = request.headers.get("X-Active-Role")
    headers = {"X-Active-Role": active_role} if active_role else {}
    if request.headers.get("Idempotency-Key"):
        headers["Idempotency-Key"] = request.headers["Idempotency-Key"]
    upstream = await client.request(
        method,
        path,
        correlation_id_context.get(),
        headers=headers,
        params=request.query_params,
        json=body,
    )
    content_type = upstream.headers.get("content-type", "application/json")
    return Response(content=upstream.content, status_code=upstream.status_code, media_type=content_type.split(";")[0])

