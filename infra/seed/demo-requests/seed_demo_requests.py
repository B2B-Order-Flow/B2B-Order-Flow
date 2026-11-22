"""Create demo requests through the public BFF API.

The script deliberately uses the API instead of writing request rows directly:
the core then creates revisions, audit entries, integrations and the initial SLA.
"""

import json
import os
import sys
import uuid
from datetime import date, timedelta
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


BASE_URL = os.getenv("BFF_URL", "http://localhost:8000").rstrip("/")
LOGIN = os.getenv("DEMO_LOGIN", "a.sokolov")
PASSWORD = os.getenv("DEMO_PASSWORD")


def call(method: str, path: str, token: str | None = None, body: dict | None = None, **headers: str) -> dict:
    request_headers = {"Accept": "application/json", **headers}
    if token:
        request_headers["Authorization"] = f"Bearer {token}"
    payload = None
    if body is not None:
        request_headers["Content-Type"] = "application/json"
        payload = json.dumps(body).encode()
    request = Request(f"{BASE_URL}{path}", data=payload, headers=request_headers, method=method)
    try:
        with urlopen(request, timeout=30) as response:
            raw = response.read()
            return json.loads(raw) if raw else {}
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} failed with {error.code}: {detail}") from error
    except URLError as error:
        raise RuntimeError(f"Cannot reach BFF at {BASE_URL}: {error.reason}") from error


def main() -> int:
    try:
        if not PASSWORD:
            raise RuntimeError("Set DEMO_PASSWORD before running the demo request seed")
        login = call("POST", "/api/v1/auth/login", body={"login": LOGIN, "password": PASSWORD})
        token = login["accessToken"]
        user = call("GET", "/api/v1/me", token=token)
        companies = user.get("companies", [])
        # CompanyBrief intentionally does not expose INN. The default demo user
        # belongs to one company; callers with several memberships can select
        # the company by its visible name.
        company_name = os.getenv("DEMO_COMPANY_NAME")
        company = next((item for item in companies if item.get("name") == company_name), None) if company_name else None
        company = company or (companies[0] if len(companies) == 1 else None)
        if company is None:
            raise RuntimeError("Set DEMO_COMPANY_NAME when the demo user belongs to several companies")

        draft = call(
            "POST",
            "/api/v1/requests",
            token=token,
            body={
                "companyId": company["id"],
                "address": "г. Москва, ул. Демонстрационная, д. 1",
                "requestedSpeedMbps": 100,
                "desiredDate": (date.today() + timedelta(days=30)).isoformat(),
                "contactName": "Алексей Соколов",
                "contactPhone": "+79990000001",
                "contractPreference": "OFFER",
                "clientComment": "Демо-заявка SC-01",
            },
            **{"Idempotency-Key": str(uuid.uuid4())},
        )
        request_id = draft["id"]
        submitted = call(
            "POST",
            f"/api/v1/requests/{request_id}/submit",
            token=token,
            body={"version": draft.get("version", 0)},
        )
        print(json.dumps({"requestId": request_id, "status": submitted.get("status")}, ensure_ascii=False))
        return 0
    except (KeyError, RuntimeError, ValueError) as error:
        print(f"demo request seed failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
