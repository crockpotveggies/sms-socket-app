package com.smssocketapp.gateway

import org.json.JSONObject

data class GatewayCallDescriptor(
  val number: String,
  val displayName: String,
  val direction: String,
  val state: String,
  val isMuted: Boolean,
  val route: String,
  val isConference: Boolean,
)

data class GatewayCallSnapshot(
  val callId: String,
  val number: String,
  val displayName: String,
  val direction: String,
  val state: String,
  val isMuted: Boolean,
  val route: String,
  val isConference: Boolean,
  val canAnswer: Boolean,
  val canReject: Boolean,
  val canDisconnect: Boolean,
) {
  fun toJson(): JSONObject =
    JSONObject()
      .put("callId", callId)
      .put("number", number)
      .put("displayName", displayName)
      .put("direction", direction)
      .put("state", state)
      .put("isMuted", isMuted)
      .put("route", route)
      .put("isConference", isConference)
      .put("canAnswer", canAnswer)
      .put("canReject", canReject)
      .put("canDisconnect", canDisconnect)
}

data class GatewayRecentCall(
  val id: String,
  val number: String,
  val displayName: String,
  val direction: String,
  val timestamp: Long,
  val durationSeconds: Long,
) {
  fun toJson(): JSONObject =
    JSONObject()
      .put("id", id)
      .put("number", number)
      .put("displayName", displayName)
      .put("direction", direction)
      .put("timestamp", timestamp)
      .put("durationSeconds", durationSeconds)
}

object GatewayCallStateMapper {
  fun toSnapshot(
    callId: String,
    descriptor: GatewayCallDescriptor,
  ): GatewayCallSnapshot {
    val isRinging = descriptor.state == "ringing"
    val canDisconnect =
      descriptor.state != "disconnected" && descriptor.state != "disconnecting"

    return GatewayCallSnapshot(
      callId = callId,
      number = descriptor.number,
      displayName = descriptor.displayName,
      direction = descriptor.direction,
      state = descriptor.state,
      isMuted = descriptor.isMuted,
      route = descriptor.route,
      isConference = descriptor.isConference,
      canAnswer = isRinging,
      canReject = isRinging,
      canDisconnect = canDisconnect,
    )
  }

  fun normalizeState(state: Int): String =
    when (state) {
      android.telecom.Call.STATE_NEW -> "new"
      android.telecom.Call.STATE_DIALING -> "dialing"
      android.telecom.Call.STATE_RINGING -> "ringing"
      android.telecom.Call.STATE_HOLDING -> "holding"
      android.telecom.Call.STATE_ACTIVE -> "active"
      android.telecom.Call.STATE_DISCONNECTING -> "disconnecting"
      android.telecom.Call.STATE_DISCONNECTED -> "disconnected"
      android.telecom.Call.STATE_SELECT_PHONE_ACCOUNT -> "select_phone_account"
      android.telecom.Call.STATE_CONNECTING -> "connecting"
      else -> "unknown"
    }

  fun normalizeDirection(direction: Int, fallbackState: String): String =
    when (direction) {
      android.telecom.Call.Details.DIRECTION_INCOMING -> "incoming"
      android.telecom.Call.Details.DIRECTION_OUTGOING -> "outgoing"
      android.telecom.Call.Details.DIRECTION_UNKNOWN -> {
        if (fallbackState == "ringing") "incoming" else "unknown"
      }
      else -> {
        if (fallbackState == "ringing") "incoming" else "unknown"
      }
    }

  fun normalizeRoute(routeMask: Int): String =
    when {
      routeMask and android.telecom.CallAudioState.ROUTE_BLUETOOTH != 0 -> "bluetooth"
      routeMask and android.telecom.CallAudioState.ROUTE_WIRED_HEADSET != 0 -> "wired_headset"
      routeMask and android.telecom.CallAudioState.ROUTE_SPEAKER != 0 -> "speaker"
      routeMask and android.telecom.CallAudioState.ROUTE_EARPIECE != 0 -> "earpiece"
      routeMask and android.telecom.CallAudioState.ROUTE_STREAMING != 0 -> "streaming"
      else -> "unknown"
    }

  fun normalizeRecentCallDirection(type: Int): String =
    when (type) {
      android.provider.CallLog.Calls.OUTGOING_TYPE -> "outgoing"
      android.provider.CallLog.Calls.INCOMING_TYPE -> "incoming"
      android.provider.CallLog.Calls.MISSED_TYPE -> "missed"
      android.provider.CallLog.Calls.REJECTED_TYPE -> "rejected"
      android.provider.CallLog.Calls.BLOCKED_TYPE -> "blocked"
      android.provider.CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"
      android.provider.CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "answered_externally"
      else -> "unknown"
    }
}
