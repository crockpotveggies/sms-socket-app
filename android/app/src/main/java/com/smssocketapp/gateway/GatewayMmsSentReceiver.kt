package com.smssocketapp.gateway

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.klinker.android.send_message.MmsSentReceiver
import org.json.JSONObject

class GatewayMmsSentReceiver : MmsSentReceiver() {
  override fun onMessageStatusUpdated(context: Context, intent: Intent, resultCode: Int) {
    val messageId = intent.getStringExtra(SmsGatewayCore.EXTRA_MESSAGE_ID) ?: return
    val pendingPayload = SmsGatewayCore.buildMmsFailurePayload(context, intent)

    if (resultCode == Activity.RESULT_OK) {
      val uriString = intent.getStringExtra(EXTRA_CONTENT_URI)
      val message =
        uriString?.let { GatewayMessageRepository(context).findMessageByUri(android.net.Uri.parse(it)) }
          ?: JSONObject(pendingPayload.toString())
      GatewayRuntime.recordEvent("mms.outbound.sent", message)
      PendingMessageStore(context).remove(messageId)
      return
    }

    GatewayRuntime.recordEvent(
      "mms.outbound.failed",
      pendingPayload
        .put("errorCode", resultCode)
        .put("stage", "sent"),
    )
    PendingMessageStore(context).remove(messageId)
  }
}
