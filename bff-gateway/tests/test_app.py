from json import loads

import httpx
import pytest
from httpx import ASGITransport

from app.config import Settings
from app.core_client import CoreClient
from app.main import app, get_core_client


@pytest.fixture
def client():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/internal/v1/requests"
        assert request.headers["x-correlation-id"] == "client-correlation"
        assert request.headers["x-internal-token"] == "test-token"
        assert request.headers["x-active-role"] == "CLIENT_OWNER"
        assert request.headers["authorization"] == "Bearer test-jwt"
        assert request.headers["idempotency-key"] == "idem-1"
        assert loads(request.content) == {"companyId": "company-1"}
        return httpx.Response(201, json={"id": "request-1"})

    transport = httpx.MockTransport(handler)

    async def override() -> CoreClient:
        return CoreClient(Settings(core_url="http://core.test", internal_token="test-token"), transport=transport)

    app.dependency_overrides[get_core_client] = override
    yield ASGITransport(app=app)
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_create_request_proxies_headers_and_body(client):
    async with httpx.AsyncClient(transport=client, base_url="http://test") as api:
        response = await api.post(
            "/api/v1/requests",
            headers={
                "X-Correlation-Id": "client-correlation",
                "X-Active-Role": "CLIENT_OWNER",
                "Authorization": "Bearer test-jwt",
                "Idempotency-Key": "idem-1",
            },
            json={"companyId": "company-1"},
        )

    assert response.status_code == 201
    assert response.json() == {"id": "request-1"}
    assert response.headers["x-correlation-id"] == "client-correlation"


@pytest.mark.asyncio
async def test_empty_body_returns_problem(client):
    async with httpx.AsyncClient(transport=client, base_url="http://test") as api:
        response = await api.post("/api/v1/companies")

    assert response.status_code == 400
    assert response.headers["content-type"].startswith("application/problem+json")
    assert response.json()["code"] == "MALFORMED_REQUEST"
    assert response.headers["x-correlation-id"]


@pytest.mark.asyncio
async def test_malformed_json_returns_problem(client):
    async with httpx.AsyncClient(transport=client, base_url="http://test") as api:
        response = await api.post("/api/v1/companies", content=b"{broken")

    assert response.status_code == 400
    assert response.json()["code"] == "MALFORMED_REQUEST"


@pytest.mark.asyncio
async def test_core_5xx_becomes_internal_error():
    async def override() -> CoreClient:
        return CoreClient(
            Settings(core_url="http://core.test", internal_token="test-token"),
            transport=httpx.MockTransport(lambda _: httpx.Response(500, text="proxy error")),
        )

    app.dependency_overrides[get_core_client] = override
    try:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as api:
            response = await api.get("/api/v1/me", headers={"X-Correlation-Id": "error-correlation"})
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 500
    assert response.json()["code"] == "INTERNAL_ERROR"
    assert response.headers["x-correlation-id"] == "error-correlation"
