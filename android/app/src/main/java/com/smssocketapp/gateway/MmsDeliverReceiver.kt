package com.smssocketapp.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

class MmsDeliverReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    GatewayRuntime.recordEvent(
      "gateway.error",
      JSONObject().put("message", "MMS delivery broadcast received, but MMS parsing is not implemented in v1."),
    )
  }
}
