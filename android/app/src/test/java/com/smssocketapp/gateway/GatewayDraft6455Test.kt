package com.smssocketapp.gateway

import org.java_websocket.handshake.HandshakeImpl1Client
import org.java_websocket.handshake.HandshakeImpl1Server
import org.junit.Assert.assertEquals
import org.junit.Test

class GatewayDraft6455Test {
  @Test
  fun `server handshake forces standards friendly connection header`() {
    val request =
      HandshakeImpl1Client().apply {
        put("Connection", "Upgrade,Keep-Alive")
        put("Upgrade", "websocket")
        put("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
        put("Sec-WebSocket-Version", "13")
        resourceDescriptor = "/"
      }
    val response = HandshakeImpl1Server()

    GatewayDraft6455().postProcessHandshakeResponseAsServer(request, response)

    assertEquals("Upgrade", response.getFieldValue("Connection"))
    assertEquals("websocket", response.getFieldValue("Upgrade"))
  }
}
