import asyncio
from collections.abc import Mapping
from typing import Any

import httpx

from .config import Settings
from .errors import CoreClientError


class CoreClient:
    def __init__(self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None) -> None:
        self.settings = settings
        self.transport = transport

    async def request(
        self,
        method: str,
        path: str,
        correlation_id: str,
        headers: Mapping[str, str] | None = None,
        **kwargs: Any,
    ) -> httpx.Response:
        timeout = httpx.Timeout(self.settings.http_timeout_seconds, connect=self.settings.http_connect_timeout_seconds)
        request_headers = {
            "X-Correlation-Id": correlation_id,
            "X-Internal-Token": self.settings.internal_token,
        }
        if headers:
            request_headers.update(headers)
        attempts = 2 if method.upper() == "GET" else 1
        last_error: Exception | None = None
        async with httpx.AsyncClient(
            base_url=self.settings.core_url, timeout=timeout, transport=self.transport
        ) as client:
            for attempt in range(attempts):
                try:
                    return await client.request(method, path, headers=request_headers, **kwargs)
                except httpx.TimeoutException as exc:
                    last_error = exc
                    if attempt + 1 < attempts:
                        await asyncio.sleep(0.3)
                except httpx.RequestError as exc:
                    last_error = exc
                    if attempt + 1 < attempts:
                        await asyncio.sleep(0.3)
        if isinstance(last_error, httpx.TimeoutException):
            raise CoreClientError(504, "CORE_TIMEOUT", "Ядро не ответило вовремя", correlation_id) from last_error
        raise CoreClientError(503, "CORE_UNAVAILABLE", "Ядро временно недоступно", correlation_id) from last_error
