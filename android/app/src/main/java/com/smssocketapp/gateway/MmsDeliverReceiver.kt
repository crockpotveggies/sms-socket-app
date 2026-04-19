package com.smssocketapp.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

class MmsDeliverReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val pendingResult = goAsync()
    val receivedAt = System.currentTimeMillis()

    Thread {
      try {
        Thread.sleep(1200)
        val event = GatewayMessageRepository(context).findRecentMmsEvent(receivedAt)
        if (event != null) {
          val type = event.optString("type")
          if (type.isNotBlank() && type != "mms.notification") {
            GatewayRuntime.recordEvent(type, event.getJSONObject("payload"))
          } else {
            GatewayRuntime.recordEvent(
              "gateway.event",
              JSONObject().put("message", "MMS notification received."),
            )
          }
        } else {
          GatewayRuntime.recordEvent(
            "gateway.error",
            JSONObject().put("message", "MMS broadcast received, but no stored MMS row was available."),
          )
        }

        if (GatewayConfigStore(context).load().enabled) {
          GatewayForegroundService.ensureStarted(context)
        }
      } catch (error: Exception) {
        GatewayRuntime.recordEvent(
          "gateway.error",
          JSONObject().put("message", error.message ?: "Unable to process MMS broadcast."),
        )
      } finally {
        pendingResult?.finish()
      }
    }.start()
  }
}
