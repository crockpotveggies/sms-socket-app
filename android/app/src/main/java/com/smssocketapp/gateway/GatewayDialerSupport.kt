package com.smssocketapp.gateway

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager

object GatewayDialerSupport {
  fun isRoleAvailable(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return true
    }

    val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
    return roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)
  }

  fun isDefaultDialer(context: Context): Boolean {
    val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return false
    return telecomManager.defaultDialerPackage == context.packageName
  }

  fun launchRolePrompt(
    context: Context,
    activity: Activity?,
    requestCode: Int,
  ): Boolean {
    if (isDefaultDialer(context)) {
      return true
    }

    if (activity == null) {
      return false
    }

    activity.runOnUiThread {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = activity.getSystemService(RoleManager::class.java)
        if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
          return@runOnUiThread
        }
        activity.startActivityForResult(
          roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER),
          requestCode,
        )
      } else {
        activity.startActivityForResult(
          Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(
            TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
            context.packageName,
          ),
          requestCode,
        )
      }
    }

    return true
  }
}
