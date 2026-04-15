package com.smssocketapp.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsDeliveredReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    SmsGatewayCore.handleDeliveryResult(context, intent, resultCode)
  }
}
