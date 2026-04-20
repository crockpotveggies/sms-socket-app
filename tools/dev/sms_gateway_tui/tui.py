from __future__ import annotations

import argparse
import asyncio
import base64
import json
import mimetypes
from pathlib import Path
import shlex
from datetime import datetime, timezone
from typing import Any

from prompt_toolkit import PromptSession
from prompt_toolkit.patch_stdout import patch_stdout
from prompt_toolkit.shortcuts import clear

from .asyncapi import AsyncApiConfig, load_asyncapi_config
from .client import GatewayClient


def _format_timestamp(value: Any) -> str:
    if not isinstance(value, int):
        return "-"
    return datetime.fromtimestamp(value / 1000, tz=timezone.utc).astimezone().strftime(
        "%Y-%m-%d %H:%M:%S"
    )


def _pretty_json(value: Any) -> str:
    return json.dumps(value, indent=2, sort_keys=True, ensure_ascii=True)


class SmsGatewayTui:
    def __init__(self, config: AsyncApiConfig, url: str | None = None) -> None:
        self._config = config
        self._url = url or config.default_url
        self._client = GatewayClient(self._handle_event)
        self._session = PromptSession()

    async def run(self) -> int:
        self._print_banner()

        while True:
            try:
                with patch_stdout():
                    raw = await self._session.prompt_async("sms-gateway> ")
            except (EOFError, KeyboardInterrupt):
                print()
                break

            command_line = raw.strip()
            if not command_line:
                continue

            try:
                should_exit = await self._dispatch(command_line)
            except Exception as error:
                print(f"[error] {error}")
                should_exit = False

            if should_exit:
                break

        await self._client.close()
        return 0

    async def _dispatch(self, command_line: str) -> bool:
        parts = shlex.split(command_line)
        command = parts[0].lower()
        args = parts[1:]

        if command in {"quit", "exit"}:
            return True
        if command == "help":
            self._print_help()
            return False
        if command == "clear":
            clear()
            self._print_banner()
            return False
        if command == "connect":
            if args:
                self._url = args[0]
            await self._client.connect(self._url)
            print(f"[ok] connected to {self._url} with bearer auth")
            return False
        if command == "auth":
            self._require_args(args, 1, "auth <api-key>")
            await self._client.connect(self._url, args[0])
            print(f"[ok] connected to {self._url} with bearer auth")
            return False
        if command == "state":
            response = await self._client.request_state()
            self._print_response("getGatewayState", response)
            return False
        if command == "subscriptions":
            response = await self._client.list_subscriptions()
            self._print_response("listSubscriptions", response)
            return False
        if command == "history":
            since = int(args[0]) if len(args) >= 1 else 0
            limit = int(args[1]) if len(args) >= 2 else 100
            response = await self._client.request_history(since, limit)
            self._print_history(response)
            return False
        if command == "send":
            self._require_args(args, 2, "send <phone> <message> [subscription-id]")
            destination = args[0]
            if len(args) > 2:
                body = " ".join(args[1:-1])
                try:
                    subscription_id = int(args[-1])
                except ValueError:
                    body = " ".join(args[1:])
                    subscription_id = None
            else:
                body = args[1]
                subscription_id = None
            response = await self._client.send_sms(destination, body, subscription_id)
            self._print_response("sendSms", response)
            return False
        if command == "sendmms":
            self._require_args(
                args,
                2,
                "sendmms <phone> <file-path> [message] [subscription-id]",
            )
            destination = args[0]
            attachment = self._load_attachment(args[1])
            if len(args) > 3:
                body = " ".join(args[2:-1])
                try:
                    subscription_id = int(args[-1])
                except ValueError:
                    body = " ".join(args[2:])
                    subscription_id = None
            elif len(args) == 3:
                try:
                    subscription_id = int(args[2])
                    body = None
                except ValueError:
                    body = args[2]
                    subscription_id = None
            else:
                body = None
                subscription_id = None
            response = await self._client.send_mms(destination, attachment, body, subscription_id)
            self._print_response("sendMms", response)
            return False

        raise ValueError(f"Unknown command '{command}'. Try 'help'.")

    async def _handle_event(self, message: dict[str, Any]) -> None:
        event_type = message.get("type", "unknown")
        timestamp = _format_timestamp(message.get("timestamp"))
        print(f"\n[event] {event_type} @ {timestamp}")
        payload = message.get("payload")
        if payload is not None:
            print(_pretty_json(payload))

    def _print_banner(self) -> None:
        print(f"{self._config.title} ({self._config.version})")
        print(f"Default URL: {self._url}")
        print(
            "Commands: connect, auth, send, sendmms, history, state, subscriptions, help, clear, quit"
        )

    def _print_help(self) -> None:
        print("Available commands:")
        print("  auth <api-key>             Connect with Authorization: Bearer <api-key>")
        print("  connect [ws-url]           Connect using the stored bearer API key")
        print("  send <phone> <message> [subscription-id]")
        print("                             Send AsyncAPI sendSms")
        print("  sendmms <phone> <file-path> [message] [subscription-id]")
        print("                             Send AsyncAPI sendMms")
        print("  history [since-ms] [limit] Request AsyncAPI rehydrate history")
        print("  state                      Send AsyncAPI getGatewayState")
        print("  subscriptions              Send AsyncAPI listSubscriptions")
        print("  clear                      Clear the screen")
        print("  help                       Show this help")
        print("  quit                       Exit the console")

    def _print_response(self, label: str, response: dict[str, Any]) -> None:
        ok = bool(response.get("ok"))
        print(f"[{'ok' if ok else 'fail'}] {label} -> requestId={response.get('requestId')}")
        print(_pretty_json(response.get("payload")))

    def _print_history(self, response: dict[str, Any]) -> None:
        ok = bool(response.get("ok"))
        events = response.get("payload", {}).get("events", [])
        print(f"[{'ok' if ok else 'fail'}] history -> {len(events)} event(s)")
        for event in events:
            event_type = event.get("type", "unknown")
            timestamp = _format_timestamp(event.get("timestamp"))
            payload = event.get("payload", {})
            address = payload.get("address", "-")
            body = payload.get("body", "")
            attachments = payload.get("attachments", [])
            media_note = ""
            if isinstance(attachments, list) and attachments:
                media_note = f" | attachments={len(attachments)}"
            print(f"  {timestamp} | {event_type} | {address} | {body}{media_note}")

    @staticmethod
    def _require_args(args: list[str], minimum: int, usage: str) -> None:
        if len(args) < minimum:
            raise ValueError(f"Usage: {usage}")

    @staticmethod
    def _load_attachment(path_value: str) -> dict[str, Any]:
        path = Path(path_value).expanduser().resolve()
        if not path.exists():
            raise ValueError(f"Attachment not found: {path}")

        mime_type, _ = mimetypes.guess_type(path.name)
        if not mime_type:
            raise ValueError(f"Unable to infer mime type for: {path.name}")

        return {
            "fileName": path.name,
            "mimeType": mime_type,
            "base64": base64.b64encode(path.read_bytes()).decode("ascii"),
            "sizeBytes": path.stat().st_size,
        }


async def _async_main(url: str | None = None) -> int:
    config = load_asyncapi_config()
    tui = SmsGatewayTui(config=config, url=url)
    return await tui.run()


def main() -> int:
    parser = argparse.ArgumentParser(description="Interactive SMS gateway websocket console")
    parser.add_argument("--url", help="Override the websocket URL from docs/asyncapi.yml")
    args = parser.parse_args()
    return asyncio.run(_async_main(url=args.url))
