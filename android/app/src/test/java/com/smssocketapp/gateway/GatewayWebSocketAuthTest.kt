package com.smssocketapp.gateway

import org.junit.Assert.assertEquals
import org.junit.Test
import org.java_websocket.handshake.HandshakeImpl1Client

class GatewayWebSocketAuthTest {
  @Test
  fun `extracts bearer token from authorization header`() {
    val request =
      HandshakeImpl1Client().apply {
        put("Authorization", "Bearer top-level-key")
      }

    assertEquals("top-level-key", GatewayWebSocketAuth.extractBearerToken(request))
  }

  @Test
  fun `accepts case insensitive bearer scheme`() {
    val request =
      HandshakeImpl1Client().apply {
        put("Authorization", "bearer legacy-key")
      }

    assertEquals("legacy-key", GatewayWebSocketAuth.extractBearerToken(request))
  }

  @Test
  fun `rejects non bearer authorization header`() {
    val request =
      HandshakeImpl1Client().apply {
        put("Authorization", "Basic legacy-key")
      }

    assertEquals(null, GatewayWebSocketAuth.extractBearerToken(request))
  }

  @Test
  fun `rejects missing or blank bearer token`() {
    val missing = HandshakeImpl1Client()
    val request =
      HandshakeImpl1Client().apply {
        put("Authorization", "Bearer   ")
      }

    assertEquals(null, GatewayWebSocketAuth.extractBearerToken(missing))
    assertEquals(null, GatewayWebSocketAuth.extractBearerToken(request))
  }
}
