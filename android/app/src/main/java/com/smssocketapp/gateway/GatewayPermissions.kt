package com.smssocketapp.gateway

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object GatewayPermissions {
  private val runtimePermissions =
    listOf(
      Manifest.permission.READ_SMS,
      Manifest.permission.RECEIVE_SMS,
      Manifest.permission.SEND_SMS,
      Manifest.permission.READ_PHONE_STATE,
    )

  fun missingPermissions(context: Context): List<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return emptyList()
    }

    return runtimePermissions.filter { permission ->
      ContextCompat.checkSelfPermission(context, permission) !=
        PackageManager.PERMISSION_GRANTED
    }
  }

  fun allGranted(context: Context): Boolean = missingPermissions(context).isEmpty()
}
