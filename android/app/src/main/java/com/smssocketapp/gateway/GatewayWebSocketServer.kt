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

      val validation = GatewayCommandParser.validate(type, payload)
      if (!validation.ok) {
        sendResponse(
          conn,
          requestId,
          false,
          JSONObject().put("error", validation.error ?: "Invalid request"),
        )
        return
      }
      val normalizedPayload = validation.payload

      when (type) {
        "getGatewayState" ->
          sendResponse(conn, requestId, true, GatewayStatusFactory.create(context))
        "getDialerState" ->
          sendResponse(conn, requestId, true, GatewayDialerManager.getDialerStatus(context))
        "listSubscriptions" ->
          sendResponse(
            conn,
            requestId,
            true,
            JSONObject().put("subscriptions", GatewayStatusFactory.listSubscriptions(context)),
          )
        "requestDialerRole" -> {
          val launched =
            GatewayDialerSupport.launchRolePrompt(
              context,
              GatewayRuntime.currentActivity(),
              8790,
            )
          if (!launched && !GatewayDialerSupport.isDefaultDialer(context)) {
            sendResponse(
              conn,
              requestId,
              false,
              JSONObject().put("error", "An activity is required to request the dialer role."),
            )
            return
          }

          sendResponse(
            conn,
            requestId,
            true,
            JSONObject()
              .put("requested", launched)
              .put("status", GatewayDialerManager.getDialerStatus(context)),
          )
        }
        "rehydrate" -> {
          val since = normalizedPayload.optLong("since", 0L)
          val limit = normalizedPayload.optInt("limit", 100)
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
          val destination = normalizedPayload.getString("destination")
          val body = normalizedPayload.getString("body")
          val subscriptionId =
            if (normalizedPayload.has("subscriptionId") && !normalizedPayload.isNull("subscriptionId")) {
              normalizedPayload.optInt("subscriptionId")
            } else {
              null
            }
          val result = SmsGatewayCore.enqueueOutboundSms(context, destination, body, subscriptionId)
          sendResponse(conn, requestId, true, result)
        }
        "sendMms" -> {
          val destination = normalizedPayload.getString("destination")
          val subject =
            if (normalizedPayload.has("subject") && !normalizedPayload.isNull("subject")) {
              normalizedPayload.optString("subject")
            } else {
              null
            }
          val body =
            if (normalizedPayload.has("body") && !normalizedPayload.isNull("body")) {
              normalizedPayload.optString("body")
            } else {
              ""
            }
          val subscriptionId =
            if (normalizedPayload.has("subscriptionId") && !normalizedPayload.isNull("subscriptionId")) {
              normalizedPayload.optInt("subscriptionId")
            } else {
              null
            }

          val result =
            SmsGatewayCore.enqueueOutboundMms(
              context,
              destination,
              body,
              subject,
              normalizedPayload.getJSONObject("attachment"),
              subscriptionId,
            )
          sendResponse(conn, requestId, true, result)
        }
        "placeCall" -> {
          val result =
            GatewayDialerManager.placeCall(
              context,
              normalizedPayload.getString("number"),
              normalizedPayload.optBoolean("speakerphone", false),
            )
          sendResponse(conn, requestId, true, result)
        }
        "answerCall" ->
          sendResponse(
            conn,
            requestId,
            true,
            JSONObject().put(
              "answered",
              GatewayDialerManager.answerCall(context, normalizedPayload.getString("callId")),
            ),
          )
        "rejectCall" ->
          sendResponse(
            conn,
            requestId,
            true,
            JSONObject().put(
              "rejected",
              GatewayDialerManager.rejectCall(context, normalizedPayload.getString("callId")),
            ),
          )
        "endCall" ->
          sendResponse(
            conn,
            requestId,
            true,
            JSONObject().put(
              "ended",
              GatewayDialerManager.endCall(context, normalizedPayload.getString("callId")),
            ),
          )
        "setMuted" ->
          sendResponse(
            conn,
            requestId,
            true,
            JSONObject().put(
              "muted",
              GatewayDialerManager.setMuted(
                context,
                normalizedPayload.getString("callId"),
                normalizedPayload.getBoolean("muted"),
              ),
            ),
          )
        "sendDtmf" ->
          sendResponse(
            conn,
            requestId,
            true,
            GatewayDialerManager.sendDtmf(
              context,
              normalizedPayload.getString("callId"),
              normalizedPayload.getString("digits"),
            ),
          )
        "showInCallScreen" ->
          sendResponse(
            conn,
            requestId,
            true,
            JSONObject().put(
              "shown",
              GatewayDialerManager.showInCallScreen(
                context,
                normalizedPayload.optBoolean("showDialpad", false),
              ),
            ),
          )
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
