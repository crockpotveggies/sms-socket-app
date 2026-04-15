package com.smssocketapp.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsDeliverReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
    if (messages.isEmpty()) {
      return
    }

    val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
    val address = messages.first().originatingAddress.orEmpty()
    val timestamp = messages.first().timestampMillis
    val subscriptionId =
      if (intent.hasExtra("subscription")) intent.getIntExtra("subscription", -1) else null

    SmsGatewayCore.handleIncomingSms(context, address, body, timestamp, subscriptionId)

    if (GatewayConfigStore(context).load().enabled) {
      GatewayForegroundService.ensureStarted(context)
    }
  }
}
