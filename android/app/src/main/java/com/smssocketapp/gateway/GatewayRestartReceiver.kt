package com.smssocketapp.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GatewayRestartReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (
      GatewayConfigStore(context).load().enabled &&
        GatewayStatusFactory.isDefaultSmsApp(context) &&
        GatewayPermissions.allGranted(context)
    ) {
      GatewayForegroundService.ensureStarted(context)
    }
  }
}
