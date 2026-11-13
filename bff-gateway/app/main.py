import json
import logging
import time
from contextlib import asynccontextmanager
from typing import Any

from fastapi import Depends, FastAPI, Header, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from .config import Settings, get_settings
from .core_client import CoreClient
from .errors import CoreClientError, core_client_error_handler, internal_error_handler, validation_error_handler
from .headers import correlation_id_context, correlation_middleware
from .models import (
    AssignStageCommand,
    Command,
    CommercialTerms,
    ContractConcludedCommand,
    DecisionCommand,
    PauseCommand,
    TechResultCommand,
)
from .proxy import proxy

logger = logging.getLogger("bff-gateway")
logging.basicConfig(
    level=logging.INFO,
    format="%(message)s",
)


@asynccontextmanager
async def lifespan(_: FastAPI):
    get_settings()
    yield


app = FastAPI(
    title="B2B Order Flow BFF",
    version="0.1.0",
    lifespan=lifespan,
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)
app.middleware("http")(correlation_middleware)
app.add_exception_handler(CoreClientError, core_client_error_handler)
app.add_exception_handler(RequestValidationError, validation_error_handler)
app.add_exception_handler(Exception, internal_error_handler)


@app.middleware("http")
async def access_log_middleware(request: Request, call_next):
    started = time.perf_counter()
    response = await call_next(request)
    logger.info(
        json.dumps(
            {
                "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                "level": "INFO",
                "service": "bff-gateway",
                "message": "request completed",
                "method": request.method,
                "path": request.url.path,
                "status": response.status_code,
                "durationMs": round((time.perf_counter() - started) * 1000),
                "correlationId": response.headers.get("X-Correlation-Id", ""),
            },
            ensure_ascii=False,
        )
    )
    return response


@app.get("/api/v1/openapi.json", include_in_schema=False)
async def openapi_json(authorization: str | None = Header(default=None)):
    if not authorization or not authorization.lower().startswith("bearer "):
        return JSONResponse(
            status_code=401,
            content={"code": "UNAUTHORIZED", "detail": "Требуется Bearer-токен", "retryable": False},
            media_type="application/problem+json",
            headers={"X-Correlation-Id": correlation_id_context.get()},
        )
    return JSONResponse(app.openapi())


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
        raise CoreClientError(400, "MALFORMED_REQUEST", "Тело запроса обязательно", correlation_id_context.get(), False)
    try:
        return await request.json()
    except (ValueError, json.JSONDecodeError) as exc:
        raise CoreClientError(
            400, "MALFORMED_REQUEST", "Тело запроса содержит некорректный JSON", correlation_id_context.get(), False
        ) from exc


@app.api_route("/api/v1/auth/login", methods=["POST"])
async def login(request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "POST", "/api/v1/auth/login", body=await read_body(request))


@app.api_route("/api/v1/auth/register", methods=["POST"])
async def register(request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "POST", "/api/v1/auth/register", body=await read_body(request))


@app.api_route("/api/v1/me", methods=["GET"])
async def me(request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "GET", "/api/v1/me")


@app.api_route("/api/v1/companies", methods=["POST"])
async def register_company(request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "POST", "/api/v1/companies", body=await read_body(request))


@app.api_route("/api/v1/companies/{company_id}", methods=["GET"])
async def get_company(company_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "GET", f"/api/v1/companies/{company_id}")


@app.api_route("/api/v1/admin/companies", methods=["GET"])
async def admin_companies(request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "GET", "/api/v1/admin/companies")


@app.api_route("/api/v1/admin/companies/{company_id}/confirm", methods=["POST"])
async def confirm_company(company_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(
        request, client, "POST", f"/api/v1/admin/companies/{company_id}/confirm", body=await read_body(request)
    )


@app.api_route("/api/v1/admin/companies/{company_id}/reject", methods=["POST"])
async def reject_company(company_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(
        request, client, "POST", f"/api/v1/admin/companies/{company_id}/reject", body=await read_body(request)
    )


@app.get("/api/v1/requests", operation_id="list_requests")
async def list_requests(request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "GET", "/api/v1/requests")


@app.post("/api/v1/requests", operation_id="create_request")
async def create_request(request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "POST", "/api/v1/requests", body=await read_body(request))


@app.api_route("/api/v1/requests/{request_id}", methods=["GET"])
async def get_request(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "GET", f"/api/v1/requests/{request_id}")


@app.patch("/api/v1/requests/{request_id}", operation_id="update_request")
async def update_request(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "PATCH", f"/api/v1/requests/{request_id}", body=await read_body(request))


@app.delete("/api/v1/requests/{request_id}", operation_id="delete_request")
async def delete_request(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "DELETE", f"/api/v1/requests/{request_id}")


@app.api_route("/api/v1/requests/{request_id}/recheck-data", methods=["POST"])
async def recheck_data(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(
        request, client, "POST", f"/api/v1/requests/{request_id}/recheck-data", body=await read_body(request)
    )


@app.api_route("/api/v1/requests/{request_id}/history", methods=["GET"])
async def request_history(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "GET", f"/api/v1/requests/{request_id}/history")


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


async def validated_body(request: Request, model: type[Command]):
    try:
        return model.model_validate(await read_body(request)).model_dump(mode="json", exclude_none=True)
    except ValidationError as exc:
        raise RequestValidationError(exc.errors()) from exc


@app.post("/api/v1/requests/{request_id}/commercial-terms", operation_id="set_commercial_terms")
async def commercial_terms(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(
        request,
        client,
        "POST",
        f"/api/v1/requests/{request_id}/commercial-terms",
        body=await validated_body(request, CommercialTerms),
    )


@app.get("/api/v1/requests/{request_id}/offers", operation_id="list_offers")
async def list_offers(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "GET", f"/api/v1/requests/{request_id}/offers")


@app.post("/api/v1/requests/{request_id}/offers", operation_id="add_offer")
async def add_offer(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await request_command(request, client, f"/api/v1/requests/{request_id}/offers")


@app.post("/api/v1/requests/{request_id}/offers/{offer_id}/select", operation_id="select_offer")
async def select_offer(request_id: str, offer_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await request_command(request, client, f"/api/v1/requests/{request_id}/offers/{offer_id}/select")


@app.get("/api/v1/stages", operation_id="list_stages")
async def list_stages(request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "GET", "/api/v1/stages")


@app.get("/api/v1/stages/my", operation_id="my_stages")
async def my_stages(request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "GET", "/api/v1/stages/my")


@app.post("/api/v1/stages/{stage_id}/claim", operation_id="claim_stage")
async def claim_stage(stage_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(
        request, client, "POST", f"/api/v1/stages/{stage_id}/claim", body=await validated_body(request, Command)
    )


@app.post("/api/v1/stages/{stage_id}/assign", operation_id="assign_stage")
async def assign_stage(stage_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(
        request,
        client,
        "POST",
        f"/api/v1/stages/{stage_id}/assign",
        body=await validated_body(request, AssignStageCommand),
    )


@app.post("/api/v1/stages/{stage_id}/tech-result", operation_id="set_tech_result")
async def tech_result(stage_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(
        request,
        client,
        "POST",
        f"/api/v1/stages/{stage_id}/tech-result",
        body=await validated_body(request, TechResultCommand),
    )


@app.post("/api/v1/stages/{stage_id}/decision", operation_id="stage_decision")
async def stage_decision(stage_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(
        request,
        client,
        "POST",
        f"/api/v1/stages/{stage_id}/decision",
        body=await validated_body(request, DecisionCommand),
    )


@app.post("/api/v1/stages/{stage_id}/complete", operation_id="complete_stage")
async def complete_stage(stage_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(
        request, client, "POST", f"/api/v1/stages/{stage_id}/complete", body=await validated_body(request, Command)
    )


@app.post("/api/v1/stages/{stage_id}/activate", operation_id="activate_stage")
async def activate_stage(stage_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(
        request, client, "POST", f"/api/v1/stages/{stage_id}/activate", body=await validated_body(request, Command)
    )


async def request_command(request: Request, client: CoreClient, path: str, model: type[Command] = Command):
    return await proxy(request, client, "POST", path, body=await validated_body(request, model))


@app.post("/api/v1/requests/{request_id}/contract/sent", operation_id="contract_sent")
async def contract_sent(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await request_command(request, client, f"/api/v1/requests/{request_id}/contract/sent")


@app.post("/api/v1/requests/{request_id}/contract/concluded", operation_id="contract_concluded")
async def contract_concluded(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await request_command(
        request, client, f"/api/v1/requests/{request_id}/contract/concluded", ContractConcludedCommand
    )


@app.post("/api/v1/requests/{request_id}/contract/accept-offer", operation_id="accept_offer")
async def accept_offer(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await request_command(request, client, f"/api/v1/requests/{request_id}/contract/accept-offer")


@app.post("/api/v1/requests/{request_id}/act/sent", operation_id="act_sent")
async def act_sent(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await request_command(request, client, f"/api/v1/requests/{request_id}/act/sent")


@app.post("/api/v1/requests/{request_id}/act/signed", operation_id="act_signed")
async def act_signed(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await request_command(request, client, f"/api/v1/requests/{request_id}/act/signed")


@app.post("/api/v1/requests/{request_id}/close-by-default", operation_id="close_by_default")
async def close_by_default(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await request_command(request, client, f"/api/v1/requests/{request_id}/close-by-default")


@app.get("/api/v1/requests/{request_id}/sla", operation_id="request_sla")
async def request_sla(request_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "GET", f"/api/v1/requests/{request_id}/sla")


@app.post("/api/v1/stages/{stage_id}/sla/pause", operation_id="pause_stage_sla")
async def pause_stage_sla(stage_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await request_command(request, client, f"/api/v1/stages/{stage_id}/sla/pause", PauseCommand)


@app.post("/api/v1/stages/{stage_id}/sla/resume", operation_id="resume_stage_sla")
async def resume_stage_sla(stage_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await request_command(request, client, f"/api/v1/stages/{stage_id}/sla/resume")


@app.post("/api/v1/stages/{stage_id}/sla/extend-pause", operation_id="extend_stage_sla_pause")
async def extend_stage_sla_pause(stage_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await request_command(request, client, f"/api/v1/stages/{stage_id}/sla/extend-pause", PauseCommand)


@app.get("/api/v1/notifications", operation_id="list_notifications")
async def list_notifications(request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(request, client, "GET", "/api/v1/notifications")


@app.post("/api/v1/notifications/{notification_id}/read", operation_id="read_notification")
async def read_notification(notification_id: str, request: Request, client: CoreClient = Depends(get_core_client)):
    return await proxy(
        request,
        client,
        "POST",
        f"/api/v1/notifications/{notification_id}/read",
        body=await validated_body(request, Command),
    )


@app.post("/api/v1/notifications/read-all", operation_id="read_all_notifications")
async def read_all_notifications(request: Request, client: CoreClient = Depends(get_core_client)):
    return await request_command(request, client, "/api/v1/notifications/read-all")
