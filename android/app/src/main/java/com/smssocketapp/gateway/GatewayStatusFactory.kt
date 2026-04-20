package com.smssocketapp.gateway

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface

object GatewayStatusFactory {
  fun create(context: Context): JSONObject {
    val configStore = GatewayConfigStore(context)
    val config = configStore.load()
    val eventStore = GatewayEventStore(context)
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val dialerStatus = GatewayDialerManager.getDialerStatus(context)
    val roleState =
      GatewayStatusSlices.roleState(
        isDefaultSmsApp(context),
        GatewayDialerSupport.isDefaultDialer(context),
      )

    return JSONObject()
      .put("enabled", config.enabled)
      .put("running", GatewayRuntime.isRunning())
      .put("host", config.host)
      .put("port", config.port)
      .put("connectionCount", GatewayRuntime.connectionCount())
      .put("smsRoleGranted", roleState.optBoolean("smsRoleGranted"))
      .put("dialerRoleGranted", roleState.optBoolean("dialerRoleGranted"))
      .put("notificationPermissionGranted", notificationsGranted(context))
      .put("gatewayPermissionsGranted", GatewayPermissions.smsPermissionsGranted(context))
      .put("missingPermissions", JSONArray(GatewayPermissions.missingSmsPermissions(context)))
      .put(
        "batteryOptimizationsIgnored",
        powerManager.isIgnoringBatteryOptimizations(context.packageName),
      )
      .put("apiKeyConfigured", configStore.hasApiKey())
      .put("apiKeyPreview", config.apiKeyPreview)
      .put("addresses", localAddresses(config.host))
      .put("recentEvents", eventStore.getRecent())
      .put("inCallServiceHealthy", dialerStatus.optBoolean("inCallServiceHealthy"))
      .put("activeCalls", dialerStatus.optJSONArray("activeCalls"))
      .put("dialerMissingPermissions", dialerStatus.optJSONArray("dialerMissingPermissions"))
  }

  fun isDefaultSmsApp(context: Context): Boolean =
    Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

  fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED

  fun localAddresses(host: String): JSONArray {
    val addresses = JSONArray()
    if (host != "0.0.0.0") {
      addresses.put(host)
      return addresses
    }

    NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
      networkInterface.inetAddresses.toList().forEach { address ->
        if (!address.isLoopbackAddress && address.hostAddress?.contains(":") == false) {
          addresses.put(address.hostAddress)
        }
      }
    }

    if (addresses.length() == 0) {
      addresses.put("0.0.0.0")
    }

    return addresses
  }

  fun listSubscriptions(context: Context): JSONArray {
    val array = JSONArray()
    val manager =
      context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    val subscriptions: List<SubscriptionInfo> =
      try {
        manager.activeSubscriptionInfoList ?: emptyList()
      } catch (_: SecurityException) {
        emptyList()
      }

    subscriptions.forEach { info ->
      array.put(
        JSONObject()
          .put("subscriptionId", info.subscriptionId)
          .put("carrierName", info.carrierName?.toString().orEmpty())
          .put("displayName", info.displayName?.toString().orEmpty())
          .put("countryIso", info.countryIso.orEmpty()),
      )
    }

    return array
  }
}
