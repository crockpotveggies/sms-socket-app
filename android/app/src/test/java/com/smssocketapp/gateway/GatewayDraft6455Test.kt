package com.smssocketapp.gateway

import org.java_websocket.enums.HandshakeState
import org.java_websocket.handshake.HandshakeImpl1Client
import org.java_websocket.handshake.HandshakeImpl1Server
import org.junit.Assert.assertEquals
import org.junit.Test

class GatewayDraft6455Test {
  private fun baseRequest(): HandshakeImpl1Client =
    HandshakeImpl1Client().apply {
      put("Connection", "Upgrade,Keep-Alive")
      put("Upgrade", "websocket")
      put("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
      put("Sec-WebSocket-Version", "13")
      resourceDescriptor = "/"
    }

  @Test
  fun `server handshake forces standards friendly connection header`() {
    val request = baseRequest().apply { put("Authorization", "Bearer secret") }
    val response = HandshakeImpl1Server()

    GatewayDraft6455 { token -> token == "secret" }.postProcessHandshakeResponseAsServer(request, response)

    assertEquals("Upgrade", response.getFieldValue("Connection"))
    assertEquals("websocket", response.getFieldValue("Upgrade"))
  }

  @Test
  fun `server handshake accepts valid bearer auth`() {
    val request = baseRequest().apply { put("Authorization", "Bearer secret") }

    val result = GatewayDraft6455 { token -> token == "secret" }.acceptHandshakeAsServer(request)

    assertEquals(HandshakeState.MATCHED, result)
  }

  @Test
  fun `server handshake rejects missing bearer auth`() {
    val result = GatewayDraft6455 { token -> token == "secret" }.acceptHandshakeAsServer(baseRequest())

    assertEquals(HandshakeState.NOT_MATCHED, result)
  }
}
