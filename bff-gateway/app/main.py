import json
from typing import Any

from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError

from .config import Settings, get_settings
from .core_client import CoreClient
from .errors import CoreClientError, core_client_error_handler, internal_error_handler, validation_error_handler
from .headers import correlation_id_context, correlation_middleware
from .proxy import proxy

app = FastAPI(title="B2B Order Flow BFF", version="0.1.0")
app.middleware("http")(correlation_middleware)
app.add_exception_handler(CoreClientError, core_client_error_handler)
app.add_exception_handler(RequestValidationError, validation_error_handler)
app.add_exception_handler(Exception, internal_error_handler)


def get_core_client(settings: Settings = Depends(get_settings)) -> CoreClient:
    return CoreClient(settings)


@app.get("/health")
async def health(client: CoreClient = Depends(get_core_client)) -> dict[str, Any]:
    try:
        response = await client.request("GET", "/actuator/health", correlation_id_context.get())
        if response.status_code >= 400:
            return {"status": "degraded", "core": "unhealthy"}
        try:
            core = response.json()
        except (ValueError, json.JSONDecodeError):
            return {"status": "degraded", "core": "invalid_response"}
        return {"status": "ok", "core": core}
    except CoreClientError:
        return {"status": "degraded", "core": "unavailable"}


async def read_body(request: Request) -> Any:
    if not await request.body():
        return None
    try:
        return await request.json()
    except (ValueError, json.JSONDecodeError) as exc:
        raise CoreClientError(
            400, "MALFORMED_REQUEST", "Тело запроса содержит некорректный JSON", correlation_id_context.get(), False
        ) from exc


@app.api_route("/api/v1/auth/login", methods=["POST"])
async def login(request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "POST", "/api/v1/auth/login", body=await read_body(request))


@app.api_route("/api/v1/companies", methods=["POST"])
async def register_company(request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "POST", "/api/v1/companies", body=await read_body(request))


@app.api_route("/api/v1/requests", methods=["GET", "POST"])
async def requests(request: Request, client: CoreClient = Depends(get_core_client)):
    body = await read_body(request) if request.method == "POST" else None
    return await proxy(request, client, request.method, "/api/v1/requests", body=body)


@app.api_route("/api/v1/requests/{request_id}", methods=["GET"])
async def get_request(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "GET", f"/api/v1/requests/{request_id}")


@app.api_route("/api/v1/requests/{request_id}/submit", methods=["POST"])
async def submit_request(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "POST", f"/api/v1/requests/{request_id}/submit", body=await read_body(request))


@app.api_route("/api/v1/requests/{request_id}/claim", methods=["POST"])
async def claim_request(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "POST", f"/api/v1/requests/{request_id}/claim", body=await read_body(request))


@app.api_route("/api/v1/requests/{request_id}/assign-manager", methods=["POST"])
async def assign_manager(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(
        request, client, "POST", f"/api/v1/requests/{request_id}/assign-manager", body=await read_body(request)
    )
