package com.smssocketapp.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_MY_PACKAGE_REPLACED -> {
        if (
          GatewayConfigStore(context).load().enabled &&
            GatewayStatusFactory.isDefaultSmsApp(context) &&
            GatewayPermissions.smsPermissionsGranted(context)
        ) {
          GatewayForegroundService.ensureStarted(context)
        }
      }
    }
  }
}
