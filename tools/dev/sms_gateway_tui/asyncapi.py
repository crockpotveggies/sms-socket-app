from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


def project_root() -> Path:
    return Path(__file__).resolve().parents[3]


def default_spec_path() -> Path:
    return project_root() / "docs" / "asyncapi.yml"


@dataclass(frozen=True)
class AsyncApiCommand:
    name: str
    description: str


@dataclass(frozen=True)
class AsyncApiConfig:
    title: str
    version: str
    default_url: str
    commands: dict[str, AsyncApiCommand]
    event_types: tuple[str, ...]


def _resolve_message_type(messages: dict[str, Any], message_key: str) -> str:
    payload = messages[message_key]["payload"]
    for item in payload.get("allOf", []):
        props = item.get("properties", {})
        if "type" in props and "const" in props["type"]:
            return str(props["type"]["const"])
    props = payload.get("properties", {})
    if "type" in props and "const" in props["type"]:
        return str(props["type"]["const"])
    raise ValueError(f"Unable to resolve type for AsyncAPI message '{message_key}'")


def load_asyncapi_config(spec_path: Path | None = None) -> AsyncApiConfig:
    spec_file = spec_path or default_spec_path()
    with spec_file.open("r", encoding="utf-8") as handle:
        spec = yaml.safe_load(handle)

    server = spec["servers"]["localGateway"]
    host_template = server["host"]
    phone_ip = server["variables"]["phoneIp"]["default"]
    channel_address = spec["channels"]["gateway"]["address"]
    default_url = f"{server['protocol']}://{host_template.format(phoneIp=phone_ip)}{channel_address}"

    messages = spec["components"]["messages"]
    commands = {
        "authenticate": AsyncApiCommand("authenticate", "Authenticate the websocket session."),
        "getGatewayState": AsyncApiCommand("getGatewayState", "Fetch gateway status."),
        "listSubscriptions": AsyncApiCommand("listSubscriptions", "List SIM subscriptions."),
        "rehydrate": AsyncApiCommand("rehydrate", "Request message history since a timestamp."),
        "sendSms": AsyncApiCommand("sendSms", "Send an outbound SMS."),
        "ack": AsyncApiCommand("ack", "Acknowledge an event."),
    }

    event_types = tuple(
        _resolve_message_type(messages, name)
        for name in (
            "Response",
            "SmsReceived",
            "SmsOutboundAccepted",
            "SmsOutboundSent",
            "SmsOutboundDelivered",
            "SmsOutboundFailed",
            "GatewayState",
        )
    )

    return AsyncApiConfig(
        title=str(spec["info"]["title"]),
        version=str(spec["info"]["version"]),
        default_url=default_url,
        commands=commands,
        event_types=event_types,
    )
