from pathlib import Path
import tempfile
import textwrap
import unittest

from tools.dev.sms_gateway_tui.asyncapi import load_asyncapi_config


class AsyncApiConfigTests(unittest.TestCase):
    def test_loads_default_url_and_message_types(self) -> None:
        spec = textwrap.dedent(
            """
            asyncapi: 3.0.0
            info:
              title: Example Gateway
              version: 1.2.3
            servers:
              localGateway:
                host: "{phoneIp}:9000"
                protocol: ws
                variables:
                  phoneIp:
                    default: 10.0.0.5
            channels:
              gateway:
                address: /
            components:
              messages:
                Response:
                  payload:
                    properties:
                      type:
                        const: response
                SmsReceived:
                  payload:
                    properties:
                      type:
                        const: sms.received
                SmsOutboundAccepted:
                  payload:
                    properties:
                      type:
                        const: sms.outbound.accepted
                SmsOutboundSent:
                  payload:
                    properties:
                      type:
                        const: sms.outbound.sent
                SmsOutboundDelivered:
                  payload:
                    properties:
                      type:
                        const: sms.outbound.delivered
                SmsOutboundFailed:
                  payload:
                    properties:
                      type:
                        const: sms.outbound.failed
                GatewayState:
                  payload:
                    properties:
                      type:
                        const: gateway.state
            """
        ).strip()

        with tempfile.TemporaryDirectory() as temp_dir:
            spec_path = Path(temp_dir) / "asyncapi.yml"
            spec_path.write_text(spec, encoding="utf-8")
            config = load_asyncapi_config(spec_path)

        self.assertEqual(config.title, "Example Gateway")
        self.assertEqual(config.version, "1.2.3")
        self.assertEqual(config.default_url, "ws://10.0.0.5:9000/")
        self.assertIn("sendSms", config.commands)
        self.assertIn("gateway.state", config.event_types)


if __name__ == "__main__":
    unittest.main()
