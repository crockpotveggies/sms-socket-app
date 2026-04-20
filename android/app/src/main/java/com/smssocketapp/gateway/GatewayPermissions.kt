package com.smssocketapp.gateway

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object GatewayPermissions {
  private val smsPermissions =
    listOf(
      Manifest.permission.READ_SMS,
      Manifest.permission.RECEIVE_MMS,
      Manifest.permission.RECEIVE_SMS,
      Manifest.permission.SEND_SMS,
      Manifest.permission.READ_PHONE_STATE,
    )

  private fun missingPermissions(
    context: Context,
    permissions: List<String>,
  ): List<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return emptyList()
    }

    return permissions.filter { permission ->
      ContextCompat.checkSelfPermission(context, permission) !=
        PackageManager.PERMISSION_GRANTED
    }
  }

  fun missingSmsPermissions(context: Context): List<String> =
    missingPermissions(context, smsPermissions)

  fun smsPermissionsGranted(context: Context): Boolean =
    missingSmsPermissions(context).isEmpty()

  fun missingDialerPermissions(context: Context): List<String> {
    val permissions = mutableListOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
    }

    return missingPermissions(context, permissions)
  }

  fun missingDialerControlPermissions(context: Context): List<String> {
    val permissions = mutableListOf(Manifest.permission.CALL_PHONE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
    }

    return missingPermissions(context, permissions)
  }

  fun dialerControlPermissionsGranted(context: Context): Boolean =
    missingDialerControlPermissions(context).isEmpty()

  fun recentCallsPermissionGranted(context: Context): Boolean =
    missingPermissions(context, listOf(Manifest.permission.READ_CALL_LOG)).isEmpty()
}
