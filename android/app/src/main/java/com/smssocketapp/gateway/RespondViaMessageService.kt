package com.smssocketapp.gateway

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder

class RespondViaMessageService : Service() {
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val recipients = intent?.data?.extractPhoneNumber().orEmpty()
    val body = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

    if (recipients.isNotBlank() && body.isNotBlank()) {
      SmsGatewayCore.enqueueOutboundSms(this, recipients, body, null)
    }

    stopSelf(startId)
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun Uri.extractPhoneNumber(): String = schemeSpecificPart.substringBefore('?')
}
