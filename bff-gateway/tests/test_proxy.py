import pytest

from app.proxy import core_path, public_location


@pytest.mark.parametrize(
    ("public", "internal"),
    [
        ("/api/v1/me", "/internal/v1/me"),
        ("/api/v1/requests/1/history", "/internal/v1/requests/1/history"),
        ("/api/v1/admin/companies/1/confirm", "/internal/v1/admin/companies/1/confirm"),
        ("/actuator/health", "/actuator/health"),
    ],
)
def test_core_path_mapping(public, internal):
    assert core_path(public) == internal


def test_location_is_exposed_with_public_prefix():
    assert public_location("/internal/v1/requests/123") == "/api/v1/requests/123"
    assert (
        public_location("https://core.example/internal/v1/requests/123")
        == "https://core.example/internal/v1/requests/123"
    )
