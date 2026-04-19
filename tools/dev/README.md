# SMS/MMS Gateway Dev Console

Python TUI for the local SMS/MMS gateway websocket defined in [`docs/asyncapi.yml`](/C:/Users/justi/Projects/sms-socket-app/docs/asyncapi.yml).

## What It Does

The TUI is a small operator console for the Android SMS gateway. It connects over websocket, authenticates with the gateway API key, sends AsyncAPI commands, and prints both command responses and pushed gateway events.

Main use cases:

- send outbound SMS and MMS messages from a terminal
- inspect gateway health and active SIM subscriptions
- request historical SMS/MMS events with `rehydrate`
- watch inbound and outbound events in real time

## Requirements

- Python 3.11+ recommended
- the Android app running on a phone on the same LAN
- the gateway started from the mobile app
- the gateway API key available for authentication

## Install

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r tools/dev/requirements.txt
```

## Run

```bash
python -m tools.dev.sms_gateway_tui
```

Override the default URL from [`docs/asyncapi.yml`](/C:/Users/justi/Projects/sms-socket-app/docs/asyncapi.yml):

```bash
python -m tools.dev.sms_gateway_tui --url ws://192.168.1.25:8787/
```

## Typical Workflow

1. Open the Android app and start the gateway service.
2. Confirm the phone IP and port, usually `8787`.
3. Launch the TUI from the repository root.
4. Authenticate with `auth <api-key>`.
5. Run `state` to verify permissions, host, connection count, and recent events.
6. Run `subscriptions` if you want to target a specific SIM.
7. Send messages or request history.

Example session:

```text
auth abc123
state
subscriptions
send +15551234567 "hello from the terminal"
sendmms +15551234567 .\photo.jpg "hello with media"
history 0 20
```

## Commands

- `connect [ws-url]`
- `auth <api-key>`
- `send <phone> <message> [subscription-id]`
- `sendmms <phone> <file-path> [message] [subscription-id]`
- `history [since-ms] [limit]`
- `state`
- `subscriptions`
- `help`
- `quit`

If `ws-url` is omitted, the console uses the AsyncAPI server default from `docs/asyncapi.yml`.

## Command Reference

### `connect [ws-url]`

Connect to the gateway websocket. If you omit `ws-url`, the console uses the default value from the AsyncAPI file.

Examples:

```text
connect
connect ws://192.168.1.25:8787/
```

### `auth <api-key>`

Authenticates the session by sending the AsyncAPI `authenticate` message. Most commands will fail until this succeeds.

Example:

```text
auth abc123
```

### `state`

Requests `getGatewayState`. The response includes flags such as `enabled`, `running`, permission status, local addresses, and recent gateway events.

### `subscriptions`

Requests `listSubscriptions`. Useful on dual-SIM devices because `send` accepts an optional `subscription-id`.

### `send <phone> <message> [subscription-id]`

Sends the AsyncAPI `sendSms` command.

Examples:

```text
send +15551234567 "hello from the terminal"
send +15551234567 "use SIM 2 for this" 2
```

Notes:

- the phone number should be in the format your Android device expects
- quote the message if it contains spaces
- if the last argument is an integer, the TUI treats it as `subscriptionId`

### `history [since-ms] [limit]`

Requests `rehydrate` and prints summarized events from `response.payload.events`.

Examples:

```text
history
history 0 20
history 1713139200000 100
```

Notes:

- `since-ms` is a Unix timestamp in milliseconds
- `limit` defaults to `100`
- the Android gateway bounds history requests internally

### `sendmms <phone> <file-path> [message] [subscription-id]`

Reads a local file, base64-encodes it, infers the MIME type from the filename, and sends AsyncAPI `sendMms`.

Examples:

```text
sendmms +15551234567 .\photo.jpg
sendmms +15551234567 .\photo.jpg "caption from the console"
sendmms +15551234567 .\clip.mp4 "use SIM 2" 2
```

Notes:

- v1 supports one attachment per outbound MMS
- supported MIME families are `image/*`, `video/*`, `audio/*`, and `application/pdf`
- the local file must be small enough for the gateway's 1 MB attachment cap after any normalization

### `clear`

Clears the terminal and redraws the TUI banner.

### `help`

Shows the built-in command summary.

### `quit`

Closes the TUI session.

## Output Model

The console displays two kinds of output:

- command responses, which come back as AsyncAPI `response` messages
- server-pushed events such as `sms.received`, `mms.received`, `sms.outbound.accepted`, `mms.outbound.sent`, `mms.outbound.delivered`, `sms.outbound.failed`, and `gateway.state`

That means you can leave the console open after sending a message and watch delivery updates arrive without polling. For once, the websocket is doing exactly what it was hired to do.

## Troubleshooting

- If `auth` fails, verify the API key shown in the Android app.
- If `connect` fails, confirm the phone and workstation are on the same LAN and the gateway is running.
- If `send` fails with an authorization or validation error, authenticate first and make sure both destination and message body are non-empty.
- If `sendmms` fails, verify the file path exists locally, the file extension resolves to a supported MIME type, and the attachment stays within the gateway size limit.
- If `history` is empty, try `history 0 100` to request the earliest available events.
- If a device has multiple SIMs, run `subscriptions` and pass the correct `subscription-id` to `send`.

## Files

- [`requirements.txt`](/C:/Users/justi/Projects/sms-socket-app/tools/dev/requirements.txt): Python dependencies for the TUI
- [`sms_gateway_tui/tui.py`](/C:/Users/justi/Projects/sms-socket-app/tools/dev/sms_gateway_tui/tui.py): interactive prompt and command dispatch
- [`sms_gateway_tui/client.py`](/C:/Users/justi/Projects/sms-socket-app/tools/dev/sms_gateway_tui/client.py): async websocket client
- [`sms_gateway_tui/asyncapi.py`](/C:/Users/justi/Projects/sms-socket-app/tools/dev/sms_gateway_tui/asyncapi.py): AsyncAPI loader and default URL discovery
