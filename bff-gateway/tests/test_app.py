import httpx
import pytest
from httpx import ASGITransport

from app.config import Settings
from app.core_client import CoreClient
from app.main import app, get_core_client


@pytest.fixture
def client():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.headers["x-correlation-id"] == "client-correlation"
        assert request.headers["x-internal-token"] == "test-token"
        assert request.headers["x-active-role"] == "CLIENT"
        assert request.headers["idempotency-key"] == "idem-1"
        return httpx.Response(201, json={"id": "request-1"})

    transport = httpx.MockTransport(handler)

    async def override() -> CoreClient:
        return CoreClient(Settings(core_url="http://core.test", internal_token="test-token"))

    app.dependency_overrides[get_core_client] = override
    original_client = httpx.AsyncClient
    httpx.AsyncClient = lambda **_: original_client(transport=transport)
    yield ASGITransport(app=app)
    httpx.AsyncClient = original_client
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_create_request_proxies_headers_and_body(client):
    async with httpx.AsyncClient(transport=client, base_url="http://test") as api:
        response = await api.post(
            "/api/v1/requests",
            headers={
                "X-Correlation-Id": "client-correlation",
                "X-Active-Role": "CLIENT",
                "Idempotency-Key": "idem-1",
            },
            json={"companyId": "company-1"},
        )

    assert response.status_code == 201
    assert response.json() == {"id": "request-1"}
    assert response.headers["x-correlation-id"] == "client-correlation"
