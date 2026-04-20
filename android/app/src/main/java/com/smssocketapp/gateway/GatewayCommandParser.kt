package com.smssocketapp.gateway

import org.json.JSONObject

data class GatewayCommandValidation(
  val ok: Boolean,
  val payload: JSONObject = JSONObject(),
  val error: String? = null,
)

object GatewayCommandParser {
  fun validate(
    type: String,
    payload: JSONObject,
  ): GatewayCommandValidation =
    when (type) {
      "sendSms" -> validateSendSms(payload)
      "sendMms" -> validateSendMms(payload)
      "placeCall" -> validatePlaceCall(payload)
      "answerCall",
      "rejectCall",
      "endCall" -> validateCallId(payload)
      "setMuted" -> validateMuted(payload)
      "showInCallScreen" -> GatewayCommandValidation(
        ok = true,
        payload =
          JSONObject().put(
            "showDialpad",
            if (payload.has("showDialpad")) payload.optBoolean("showDialpad") else false,
          ),
      )
      else -> GatewayCommandValidation(ok = true, payload = payload)
    }

  private fun validateSendSms(payload: JSONObject): GatewayCommandValidation {
    val destination = payload.optString("destination").trim()
    val body = payload.optString("body")
    if (destination.isBlank() || body.isBlank()) {
      return GatewayCommandValidation(
        ok = false,
        error = "destination and body are required",
      )
    }

    return GatewayCommandValidation(
      ok = true,
      payload =
        JSONObject()
          .put("destination", destination)
          .put("body", body)
          .put(
            "subscriptionId",
            if (payload.has("subscriptionId")) payload.optInt("subscriptionId") else JSONObject.NULL,
          ),
    )
  }

  private fun validateSendMms(payload: JSONObject): GatewayCommandValidation {
    val destination = payload.optString("destination").trim()
    if (destination.isBlank() || !payload.has("attachment")) {
      return GatewayCommandValidation(
        ok = false,
        error = "destination and attachment are required",
      )
    }

    return GatewayCommandValidation(
      ok = true,
      payload =
        JSONObject()
          .put("destination", destination)
          .put("body", if (payload.has("body")) payload.optString("body") else "")
          .put("subject", if (payload.has("subject")) payload.optString("subject") else JSONObject.NULL)
          .put("attachment", payload.getJSONObject("attachment"))
          .put(
            "subscriptionId",
            if (payload.has("subscriptionId")) payload.optInt("subscriptionId") else JSONObject.NULL,
          ),
    )
  }

  private fun validatePlaceCall(payload: JSONObject): GatewayCommandValidation {
    val number = payload.optString("number").trim()
    if (number.isBlank()) {
      return GatewayCommandValidation(ok = false, error = "number is required")
    }

    return GatewayCommandValidation(
      ok = true,
      payload =
        JSONObject()
          .put("number", number)
          .put(
            "speakerphone",
            if (payload.has("speakerphone")) payload.optBoolean("speakerphone") else false,
          ),
    )
  }

  private fun validateCallId(payload: JSONObject): GatewayCommandValidation {
    val callId = payload.optString("callId").trim()
    if (callId.isBlank()) {
      return GatewayCommandValidation(ok = false, error = "callId is required")
    }

    return GatewayCommandValidation(ok = true, payload = JSONObject().put("callId", callId))
  }

  private fun validateMuted(payload: JSONObject): GatewayCommandValidation {
    val callId = payload.optString("callId").trim()
    val mutedValue = payload.opt("muted")
    if (callId.isBlank() || mutedValue !is Boolean) {
      return GatewayCommandValidation(ok = false, error = "callId and muted are required")
    }

    return GatewayCommandValidation(
      ok = true,
      payload = JSONObject().put("callId", callId).put("muted", mutedValue),
    )
  }
}
