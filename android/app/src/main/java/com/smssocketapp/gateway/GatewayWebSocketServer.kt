package com.smssocketapp.gateway

import android.content.Context
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.Collections

class GatewayWebSocketServer(
  private val context: Context,
  address: InetSocketAddress,
  private val authValidator: (String?) -> Boolean,
  private val onConnectionChanged: (Int) -> Unit,
  private val onEvent: (String, JSONObject) -> Unit,
) : WebSocketServer(address, listOf(GatewayDraft6455())) {
  private val authenticatedConnections =
    Collections.synchronizedSet(mutableSetOf<WebSocket>())

  override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
    onConnectionChanged(connections.size)
  }

  override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
    authenticatedConnections.remove(conn)
    onConnectionChanged(connections.size)
  }

  override fun onMessage(conn: WebSocket, message: String) {
    try {
      val request = JSONObject(message)
      val type = request.optString("type")
      val requestId = request.optString("requestId")
      val auth = if (request.has("auth") && !request.isNull("auth")) request.getString("auth") else null
      val payload = request.optJSONObject("payload") ?: JSONObject()

      if (type == "authenticate") {
        if (authValidator(auth)) {
          authenticatedConnections.add(conn)
          sendResponse(conn, requestId, true, JSONObject().put("authenticated", true))
        } else {
          sendResponse(conn, requestId, false, JSONObject().put("error", "Invalid API key"))
          conn.close(4001, "Unauthorized")
        }
        return
      }

      val authenticated = authenticatedConnections.contains(conn) || authValidator(auth)
      if (!authenticated) {
        sendResponse(conn, requestId, false, JSONObject().put("error", "Unauthorized"))
        return
      }
      authenticatedConnections.add(conn)

      when (type) {
        "getGatewayState" ->
          sendResponse(conn, requestId, true, GatewayStatusFactory.create(context))
        "listSubscriptions" ->
          sendResponse(
            conn,
            requestId,
            true,
            JSONObject().put("subscriptions", GatewayStatusFactory.listSubscriptions(context)),
          )
        "rehydrate" -> {
          val since = payload.optLong("since", 0L)
          val limit = payload.optInt("limit", 100)
          sendResponse(
            conn,
            requestId,
            true,
            JSONObject().put("events", SmsHistoryRepository(context).rehydrateSince(since, limit)),
          )
        }
        "ack" ->
          sendResponse(conn, requestId, true, JSONObject().put("acknowledged", true))
        "sendSms" -> {
          val destination = payload.optString("destination").trim()
          val body = payload.optString("body")
          if (destination.isBlank() || body.isBlank()) {
            sendResponse(
              conn,
              requestId,
              false,
              JSONObject().put("error", "destination and body are required"),
            )
            return
          }
          val subscriptionId =
            if (payload.has("subscriptionId")) payload.optInt("subscriptionId") else null
          val result = SmsGatewayCore.enqueueOutboundSms(context, destination, body, subscriptionId)
          sendResponse(conn, requestId, true, result)
        }
        else ->
          sendResponse(
            conn,
            requestId,
            false,
            JSONObject().put("error", "Unsupported command: $type"),
          )
      }
    } catch (error: Exception) {
      sendResponse(
        conn,
        "",
        false,
        JSONObject().put("error", error.message ?: "Malformed request"),
      )
      onEvent("gateway.error", JSONObject().put("message", error.message ?: "Malformed request"))
    }
  }

  override fun onError(conn: WebSocket?, ex: Exception) {
    onEvent(
      "gateway.error",
      JSONObject().put("message", ex.message ?: "Unknown socket error"),
    )
  }

  override fun onStart() {
    onEvent(
      "gateway.event",
      JSONObject().put("message", "WebSocket server listening on ${address.hostString}:${address.port}"),
    )
  }

  fun broadcastGatewayEvent(type: String, payload: JSONObject, timestamp: Long) {
    val event =
      JSONObject()
        .put("type", type)
        .put("timestamp", timestamp)
        .put("payload", payload)
    broadcastToAuthenticated(event.toString())
  }

  private fun sendResponse(
    conn: WebSocket,
    requestId: String,
    ok: Boolean,
    payload: JSONObject,
  ) {
    conn.send(
      JSONObject()
        .put("type", "response")
        .put("requestId", requestId)
        .put("ok", ok)
        .put("timestamp", System.currentTimeMillis())
        .put("payload", payload)
        .toString(),
    )
  }

  private fun broadcastToAuthenticated(message: String) {
    authenticatedConnections.toList().forEach { socket ->
      if (socket.isOpen) {
        socket.send(message)
      }
    }
  }
}
