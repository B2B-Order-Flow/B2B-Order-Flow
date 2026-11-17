import pytest

from app.config import Settings
from app.websocket import WebSocketHub


class FakeWebSocket:
    def __init__(self):
        self.messages = []
        self.closed = []

    async def send_json(self, message):
        self.messages.append(message)

    async def close(self, code=1000, reason=None):
        self.closed.append((code, reason))


@pytest.mark.asyncio
async def test_hub_routes_signal_by_user_without_leaking_user_id():
    hub = WebSocketHub(Settings(internal_token="secret"))
    socket = FakeWebSocket()
    await hub.register("user-1", socket)

    await hub.publish(
        {
            "id": "notification-1",
            "type": "STAGE_QUEUED",
            "userId": "user-1",
            "createdAt": "2026-11-17T10:00:00Z",
            "payload": {"stageId": "stage-1"},
        }
    )

    assert socket.messages == [
        {
            "id": "notification-1",
            "type": "STAGE_QUEUED",
            "createdAt": "2026-11-17T10:00:00Z",
            "payload": {"stageId": "stage-1"},
        }
    ]


@pytest.mark.asyncio
async def test_hub_closes_oldest_connection_over_limit():
    hub = WebSocketHub(Settings(internal_token="secret"), max_connections_per_user=1)
    first = FakeWebSocket()
    second = FakeWebSocket()
    await hub.register("user-1", first)
    await hub.register("user-1", second)

    assert first.closed == [(1008, "connection limit reached")]
