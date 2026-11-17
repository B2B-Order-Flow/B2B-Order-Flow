import asyncio
import json
import logging
from collections import defaultdict
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any

import httpx
from fastapi import WebSocket, WebSocketDisconnect

from .config import Settings

logger = logging.getLogger("bff-gateway.websocket")


class WebSocketHub:
    def __init__(self, settings: Settings, max_connections_per_user: int = 5) -> None:
        self.settings = settings
        self.max_connections_per_user = max_connections_per_user
        self._connections: dict[str, list[WebSocket]] = defaultdict(list)
        self._lock = asyncio.Lock()
        self._task: asyncio.Task[None] | None = None
        self._stop = asyncio.Event()
        self._last_event_id: str | None = None

    async def register(self, user_id: str, websocket: WebSocket) -> None:
        async with self._lock:
            connections = self._connections[user_id]
            if len(connections) >= self.max_connections_per_user:
                oldest = connections.pop(0)
                await oldest.close(code=1008, reason="connection limit reached")
            connections.append(websocket)

    async def unregister(self, user_id: str, websocket: WebSocket) -> None:
        async with self._lock:
            connections = self._connections.get(user_id, [])
            if websocket in connections:
                connections.remove(websocket)
            if not connections:
                self._connections.pop(user_id, None)

    async def publish(self, signal: dict[str, Any]) -> None:
        user_id = signal.get("userId")
        if not user_id:
            return
        envelope = {key: value for key, value in signal.items() if key != "userId"}
        async with self._lock:
            connections = list(self._connections.get(str(user_id), []))
        stale = []
        for websocket in connections:
            try:
                await websocket.send_json(envelope)
            except Exception:
                stale.append(websocket)
        for websocket in stale:
            await self.unregister(str(user_id), websocket)

    async def start(self) -> None:
        self._stop.clear()
        self._task = asyncio.create_task(self._event_loop())

    async def stop(self) -> None:
        self._stop.set()
        if self._task:
            self._task.cancel()
            await asyncio.gather(self._task, return_exceptions=True)
            self._task = None

    async def _event_loop(self) -> None:
        delay = 1.0
        while not self._stop.is_set():
            try:
                async for signal in self._stream_events():
                    delay = 1.0
                    self._last_event_id = signal.get("createdAt")
                    await self.publish(signal)
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("event stream disconnected")
            try:
                await asyncio.wait_for(self._stop.wait(), timeout=delay)
            except TimeoutError:
                delay = min(delay * 2, 30.0)

    async def _stream_events(self) -> AsyncIterator[dict[str, Any]]:
        headers = {"X-Internal-Token": self.settings.internal_token}
        if self._last_event_id:
            headers["Last-Event-ID"] = self._last_event_id
        async with httpx.AsyncClient(base_url=self.settings.core_url, timeout=None) as client:
            async with client.stream("GET", "/internal/v1/events/stream", headers=headers) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    payload = line.removeprefix("data:").strip()
                    if payload:
                        yield json.loads(payload)

    @asynccontextmanager
    async def connection(self, user_id: str, websocket: WebSocket):
        await self.register(user_id, websocket)
        try:
            yield
        finally:
            await self.unregister(user_id, websocket)


async def websocket_session(websocket: WebSocket, hub: WebSocketHub, user_id: str) -> None:
    async with hub.connection(user_id, websocket):
        try:
            while True:
                await websocket.receive_text()
        except WebSocketDisconnect:
            return
