from __future__ import annotations

import asyncio
import contextlib
import json
import uuid
from collections.abc import Awaitable, Callable
from typing import Any

import websockets
from websockets import WebSocketClientProtocol


JsonDict = dict[str, Any]
EventHandler = Callable[[JsonDict], Awaitable[None]]


class GatewayClient:
    def __init__(self, on_event: EventHandler) -> None:
        self._on_event = on_event
        self._socket: WebSocketClientProtocol | None = None
        self._receiver_task: asyncio.Task[None] | None = None
        self._pending: dict[str, asyncio.Future[JsonDict]] = {}
        self._write_lock = asyncio.Lock()
        self._authenticated = False

    @property
    def connected(self) -> bool:
        return self._socket is not None and not self._socket.closed

    @property
    def authenticated(self) -> bool:
        return self._authenticated

    async def connect(self, url: str) -> None:
        await self.close()
        self._socket = await websockets.connect(url)
        self._receiver_task = asyncio.create_task(self._receiver_loop())
        self._authenticated = False

    async def close(self) -> None:
        self._authenticated = False
        receiver = self._receiver_task
        self._receiver_task = None

        if self._socket is not None:
            await self._socket.close()
            self._socket = None

        if receiver is not None:
            receiver.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await receiver

        for future in self._pending.values():
            if not future.done():
                future.set_exception(ConnectionError("Socket connection closed"))
        self._pending.clear()

    async def authenticate(self, api_key: str) -> JsonDict:
        response = await self.send_command("authenticate", auth=api_key)
        self._authenticated = bool(response.get("payload", {}).get("authenticated"))
        return response

    async def request_state(self) -> JsonDict:
        return await self.send_command("getGatewayState")

    async def list_subscriptions(self) -> JsonDict:
        return await self.send_command("listSubscriptions")

    async def request_history(self, since: int, limit: int) -> JsonDict:
        return await self.send_command("rehydrate", payload={"since": since, "limit": limit})

    async def send_sms(
        self,
        destination: str,
        body: str,
        subscription_id: int | None = None,
    ) -> JsonDict:
        payload: JsonDict = {"destination": destination, "body": body}
        if subscription_id is not None:
            payload["subscriptionId"] = subscription_id
        return await self.send_command("sendSms", payload=payload)

    async def send_command(
        self,
        command_type: str,
        payload: JsonDict | None = None,
        auth: str | None = None,
    ) -> JsonDict:
        if self._socket is None:
            raise ConnectionError("Not connected")

        request_id = str(uuid.uuid4())
        request: JsonDict = {
            "type": command_type,
            "requestId": request_id,
        }
        if payload is not None:
            request["payload"] = payload
        if auth is not None:
            request["auth"] = auth

        loop = asyncio.get_running_loop()
        future: asyncio.Future[JsonDict] = loop.create_future()
        self._pending[request_id] = future

        async with self._write_lock:
            await self._socket.send(json.dumps(request))

        try:
            return await asyncio.wait_for(future, timeout=15)
        finally:
            self._pending.pop(request_id, None)

    async def _receiver_loop(self) -> None:
        assert self._socket is not None
        try:
            async for raw_message in self._socket:
                message = json.loads(raw_message)
                request_id = message.get("requestId")
                if message.get("type") == "response" and isinstance(request_id, str):
                    future = self._pending.get(request_id)
                    if future is not None and not future.done():
                        future.set_result(message)
                        continue

                await self._on_event(message)
        except asyncio.CancelledError:
            raise
        except Exception as error:
            await self._on_event(
                {
                    "type": "client.error",
                    "timestamp": None,
                    "payload": {"error": str(error)},
                }
            )
        finally:
            self._authenticated = False
            if self._socket is not None and self._socket.closed:
                self._socket = None
            for future in self._pending.values():
                if not future.done():
                    future.set_exception(ConnectionError("Socket connection closed"))
