package com.smssocketapp.gateway

import org.java_websocket.drafts.Draft_6455
import org.java_websocket.exceptions.InvalidHandshakeException
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.HandshakeBuilder
import org.java_websocket.handshake.ServerHandshakeBuilder

class GatewayDraft6455 : Draft_6455() {
  @Throws(InvalidHandshakeException::class)
  override fun postProcessHandshakeResponseAsServer(
    request: ClientHandshake,
    response: ServerHandshakeBuilder,
  ): HandshakeBuilder {
    val handshake = super.postProcessHandshakeResponseAsServer(request, response)
    response.put("Connection", "Upgrade")
    response.put("Upgrade", "websocket")
    return handshake
  }

  override fun copyInstance(): GatewayDraft6455 = GatewayDraft6455()
}
