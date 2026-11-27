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


@pytest.mark.asyncio
async def test_openapi_requires_bearer_header():
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as api:
        unauthorized = await api.get("/api/v1/openapi.json")
        authorized = await api.get("/api/v1/openapi.json", headers={"Authorization": "Bearer test-jwt"})

    assert unauthorized.status_code == 401
    assert authorized.status_code == 200
    assert "/api/v1/requests" in authorized.json()["paths"]


@pytest.mark.asyncio
async def test_week_two_command_validation_returns_problem():
    async def override() -> CoreClient:
        return CoreClient(
            Settings(core_url="http://core.test", internal_token="test-token"),
            transport=httpx.MockTransport(lambda _: httpx.Response(200, json={"ok": True})),
        )

    app.dependency_overrides[get_core_client] = override
    try:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as api:
            response = await api.post("/api/v1/stages/stage-1/tech-result", json={"version": -1, "result": "UNKNOWN"})
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_FAILED"


@pytest.mark.asyncio
async def test_commercial_terms_accepts_camel_case_fields():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["path"] = request.url.path
        captured["body"] = request.content
        return httpx.Response(200, json={"version": 2})

    transport = httpx.MockTransport(handler)

    async def override() -> CoreClient:
        return CoreClient(Settings(core_url="http://core.test", internal_token="test-token"), transport=transport)

    app.dependency_overrides[get_core_client] = override
    try:
        api_transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=api_transport, base_url="http://test") as api:
            response = await api.post(
                "/api/v1/requests/request-1/commercial-terms",
                json={"tariffType": "STANDARD", "contractType": "OFFER", "version": 1},
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    assert captured["path"] == "/internal/v1/requests/request-1/commercial-terms"
    assert b'"tariffType":"STANDARD"' in captured["body"]


@pytest.mark.asyncio
async def test_notifications_commands_forward_without_body():
    captured = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append((request.url.path, request.content))
        return httpx.Response(204)

    async def override() -> CoreClient:
        return CoreClient(
            Settings(core_url="http://core.test", internal_token="test-token"),
            transport=httpx.MockTransport(handler),
        )

    app.dependency_overrides[get_core_client] = override
    try:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as api:
            read = await api.post("/api/v1/notifications/notification-1/read")
            read_all = await api.post("/api/v1/notifications/read-all")
    finally:
        app.dependency_overrides.clear()

    assert read.status_code == 204
    assert read_all.status_code == 204
    assert captured == [
        ("/internal/v1/notifications/notification-1/read", b""),
        ("/internal/v1/notifications/read-all", b""),
    ]


@pytest.mark.asyncio
async def test_extend_pause_validates_contract_body():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["body"] = request.content
        return httpx.Response(200, json={"ok": True})

    async def override() -> CoreClient:
        return CoreClient(
            Settings(core_url="http://core.test", internal_token="test-token"),
            transport=httpx.MockTransport(handler),
        )

    app.dependency_overrides[get_core_client] = override
    try:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as api:
            valid = await api.post(
                "/api/v1/stages/stage-1/sla/extend-pause",
                json={"comment": "need more time", "version": 1},
            )
            invalid = await api.post(
                "/api/v1/stages/stage-1/sla/extend-pause",
                json={"reasonCode": "OTHER", "comment": "need more time", "version": 1},
            )
    finally:
        app.dependency_overrides.clear()

    assert valid.status_code == 200
    assert b'"comment":"need more time"' in captured["body"]
    assert invalid.status_code == 422


@pytest.mark.asyncio
async def test_week_three_stage_decision_is_proxied():
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["path"] = request.url.path
        captured["body"] = request.content
        return httpx.Response(200, json={"ok": True})

    async def override() -> CoreClient:
        return CoreClient(
            Settings(core_url="http://core.test", internal_token="test-token"),
            transport=httpx.MockTransport(handler),
        )

    app.dependency_overrides[get_core_client] = override
    try:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as api:
            response = await api.post(
                "/api/v1/stages/stage-1/decision",
                json={"decision": "APPROVE", "version": 1},
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    assert captured["path"] == "/internal/v1/stages/stage-1/decision"
    assert b'"decision":"APPROVE"' in captured["body"]


@pytest.mark.asyncio
async def test_week_three_patch_and_payloads_match_core_contract():
    captured: list[tuple[str, str, bytes]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append((request.method, request.url.path, request.content))
        return httpx.Response(200, json={"ok": True})

    async def override() -> CoreClient:
        return CoreClient(
            Settings(core_url="http://core.test", internal_token="test-token"),
            transport=httpx.MockTransport(handler),
        )

    app.dependency_overrides[get_core_client] = override
    try:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as api:
            data = await api.patch(
                "/api/v1/requests/request-1/data",
                json={"companyName": "Updated", "version": 2},
            )
            problem = await api.post(
                "/api/v1/stages/stage-1/problem",
                json={"problem": "NO_ACCESS", "comment": "Access is unavailable", "version": 3},
            )
            claim = await api.post(
                "/api/v1/requests/request-1/defect-claim",
                json={"reason": "Service defect", "version": 4},
            )
    finally:
        app.dependency_overrides.clear()

    assert data.status_code == 200
    assert problem.status_code == 200
    assert claim.status_code == 200
    assert [method for method, _, _ in captured] == ["PATCH", "POST", "POST"]
    assert captured[0][1] == "/internal/v1/requests/request-1/data"
    assert b'"problem":"NO_ACCESS"' in captured[1][2]
    assert b'"reason":"Service defect"' in captured[2][2]


@pytest.mark.asyncio
async def test_week_three_cancellation_costs_and_pdf_export_contract():
    captured: list[tuple[str, str, bytes]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append((request.method, request.url.path, request.content))
        return httpx.Response(200, json={"ok": True})

    async def override() -> CoreClient:
        return CoreClient(
            Settings(core_url="http://core.test", internal_token="test-token"),
            transport=httpx.MockTransport(handler),
        )

    app.dependency_overrides[get_core_client] = override
    try:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as api:
            cancellation = await api.post(
                "/api/v1/requests/request-1/cancellation-requests",
                json={"reasonCode": "CLIENT_OTHER", "comment": "Changed plans", "version": 1},
            )
            costs = await api.post(
                "/api/v1/cancellation-requests/cancel-1/costs",
                json={
                    "amount": "45000.00",
                    "breakdown": [{"description": "Works", "amount": "45000.00"}],
                    "comment": "Calculation",
                    "version": 2,
                },
            )
            returned = await api.post(
                "/api/v1/cancellation-requests/cancel-1/confirm-costs",
                json={"confirmed": False, "comment": "Please recalculate", "version": 3},
            )
            export = await api.post(
                "/api/v1/exports/requests",
                json={"format": "PDF", "requestId": "request-1"},
            )
    finally:
        app.dependency_overrides.clear()

    assert cancellation.status_code == 200
    assert costs.status_code == 200
    assert returned.status_code == 200
    assert export.status_code == 200
    assert b'"comment":"Changed plans"' in captured[0][2]
    assert b'"breakdown":[{"description":"Works","amount":"45000.00"}]' in captured[1][2]
    assert b'"confirmed":false' in captured[2][2]
    assert b'"requestId":"request-1"' in captured[3][2]


@pytest.mark.asyncio
async def test_pdf_export_requires_request_id():
    async def override() -> CoreClient:
        return CoreClient(
            Settings(core_url="http://core.test", internal_token="test-token"),
            transport=httpx.MockTransport(lambda _: httpx.Response(200, json={"ok": True})),
        )

    app.dependency_overrides[get_core_client] = override
    try:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as api:
            response = await api.post("/api/v1/exports/requests", json={"format": "PDF"})
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 422
