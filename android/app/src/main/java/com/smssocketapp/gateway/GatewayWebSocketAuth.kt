package com.smssocketapp.gateway

import org.java_websocket.handshake.ClientHandshake

internal object GatewayWebSocketAuth {
  private val bearerRegex = Regex("^Bearer\\s+(.+)$", RegexOption.IGNORE_CASE)

  fun extractBearerToken(
    request: ClientHandshake,
  ): String? {
    if (!request.hasFieldValue("Authorization")) {
      return null
    }

    val headerValue = request.getFieldValue("Authorization").trim()
    if (headerValue.isEmpty()) {
      return null
    }

    val match = bearerRegex.matchEntire(headerValue) ?: return null
    return match.groupValues[1].trim().takeIf { it.isNotEmpty() }
  }
}
