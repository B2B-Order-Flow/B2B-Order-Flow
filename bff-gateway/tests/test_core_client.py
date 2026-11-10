import httpx
import pytest

from app.config import Settings
from app.core_client import CoreClient
from app.errors import CoreClientError


def settings() -> Settings:
    return Settings(core_url="http://core.test", internal_token="secret")


@pytest.mark.asyncio
async def test_client_forwards_internal_and_correlation_headers(monkeypatch):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured.update(request.headers)
        return httpx.Response(200, json={"id": "request-1"})

    original_client = httpx.AsyncClient
    monkeypatch.setattr(httpx, "AsyncClient", lambda **_: original_client(transport=httpx.MockTransport(handler)))
    response = await CoreClient(settings()).request("GET", "/api/v1/requests", "corr-1")

    assert response.status_code == 200
    assert captured["x-correlation-id"] == "corr-1"
    assert captured["x-internal-token"] == "secret"


@pytest.mark.asyncio
async def test_get_is_retried_once(monkeypatch):
    calls = 0

    def handler(_: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        if calls == 1:
            raise httpx.ConnectError("temporary failure")
        return httpx.Response(200)

    original_client = httpx.AsyncClient
    monkeypatch.setattr(httpx, "AsyncClient", lambda **_: original_client(transport=httpx.MockTransport(handler)))
    response = await CoreClient(settings()).request("GET", "/health", "corr-2")

    assert response.status_code == 200
    assert calls == 2


@pytest.mark.asyncio
async def test_connection_error_becomes_core_unavailable(monkeypatch):
    def handler(_: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("offline")

    original_client = httpx.AsyncClient
    monkeypatch.setattr(httpx, "AsyncClient", lambda **_: original_client(transport=httpx.MockTransport(handler)))

    with pytest.raises(CoreClientError) as error:
        await CoreClient(settings()).request("POST", "/api/v1/requests", "corr-3")

    assert error.value.status == 503
    assert error.value.code == "CORE_UNAVAILABLE"
    assert error.value.retryable is True


@pytest.mark.asyncio
async def test_timeout_becomes_core_timeout(monkeypatch):
    def handler(_: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("slow")

    original_client = httpx.AsyncClient
    monkeypatch.setattr(httpx, "AsyncClient", lambda **_: original_client(transport=httpx.MockTransport(handler)))

    with pytest.raises(CoreClientError) as error:
        await CoreClient(settings()).request("POST", "/api/v1/requests", "corr-4")

    assert error.value.status == 504
    assert error.value.code == "CORE_TIMEOUT"

