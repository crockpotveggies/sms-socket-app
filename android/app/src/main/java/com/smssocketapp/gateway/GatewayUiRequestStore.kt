package com.smssocketapp.gateway

import org.json.JSONObject

object GatewayUiRequestStore {
  private var pendingRequest: JSONObject? = null

  @Synchronized
  fun request(
    screen: String,
    showDialpad: Boolean = false,
    showWhenLocked: Boolean = false,
  ) {
    pendingRequest =
      JSONObject()
        .put("screen", screen)
        .put("showDialpad", showDialpad)
        .put("showWhenLocked", showWhenLocked)
        .put("timestamp", System.currentTimeMillis())
  }

  @Synchronized
  fun consume(): JSONObject? {
    val request = pendingRequest
    pendingRequest = null
    return request
  }
}
