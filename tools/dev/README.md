# SMS Gateway Dev Console

Python TUI for the local SMS gateway websocket defined in [`docs/asyncapi.yml`](/C:/Users/justi/Projects/sms-socket-app/docs/asyncapi.yml).

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

## Commands

- `connect [ws-url]`
- `auth <api-key>`
- `send <phone> <message> [subscription-id]`
- `history [since-ms] [limit]`
- `state`
- `subscriptions`
- `help`
- `quit`

If `ws-url` is omitted, the console uses the AsyncAPI server default from `docs/asyncapi.yml`.
