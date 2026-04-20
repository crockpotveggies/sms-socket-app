package com.smssocketapp.gateway

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.provider.Telephony
import android.util.Log
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import org.json.JSONObject

class SmsGatewayModule(
  private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
  private var pendingRolePromise: Promise? = null
  private var pendingDialerRolePromise: Promise? = null
  private var pendingAttachmentPromise: Promise? = null

  private val activityEventListener: ActivityEventListener =
    object : BaseActivityEventListener() {
      override fun onActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
      ) {
        if (requestCode == REQUEST_SMS_ROLE) {
          val granted = GatewayStatusFactory.isDefaultSmsApp(reactContext)
          pendingRolePromise?.resolve(granted)
          pendingRolePromise = null
          GatewayRuntime.emitState()
          return
        }

        if (requestCode == REQUEST_DIALER_ROLE) {
          val granted = GatewayDialerSupport.isDefaultDialer(reactContext)
          pendingDialerRolePromise?.resolve(granted)
          pendingDialerRolePromise = null
          GatewayRuntime.emitState()
          return
        }

        if (requestCode == REQUEST_ATTACHMENT_PICK) {
          val promise = pendingAttachmentPromise
          pendingAttachmentPromise = null

          if (promise == null) {
            return
          }

          if (resultCode != Activity.RESULT_OK || data?.data == null) {
            promise.reject("ATTACHMENT_CANCELLED", "Attachment picker was cancelled.")
            return
          }

          try {
            promise.resolve(
              JsonBridge.toWritableMap(MmsSupport.pickAttachmentFromUri(reactContext, data.data!!)),
            )
          } catch (error: Exception) {
            promise.reject("ATTACHMENT_PICK_FAILED", error.message, error)
          }
        }
      }
    }

  init {
    reactContext.addActivityEventListener(activityEventListener)
    GatewayRuntime.bindReactContext(reactContext)
  }

  override fun getName(): String = "SmsGatewayModule"

  override fun invalidate() {
    GatewayRuntime.unbindReactContext()
    super.invalidate()
  }

  @ReactMethod
  fun requestSmsRole(promise: Promise) {
    if (GatewayStatusFactory.isDefaultSmsApp(reactContext)) {
      promise.resolve(true)
      return
    }

    val activity = reactApplicationContext.currentActivity
    if (activity == null) {
      promise.reject("NO_ACTIVITY", "An activity is required to request the SMS role.")
      return
    }

    pendingRolePromise = promise
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val roleManager = activity.getSystemService(RoleManager::class.java)
      activity.startActivityForResult(
        roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS),
        REQUEST_SMS_ROLE,
      )
    } else {
      val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
        putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, reactContext.packageName)
      }
      activity.startActivityForResult(intent, REQUEST_SMS_ROLE)
    }
  }

  @ReactMethod
  fun getGatewayStatus(promise: Promise) {
    promise.resolve(JsonBridge.toWritableMap(GatewayStatusFactory.create(reactContext)))
  }

  @ReactMethod
  fun requestDialerRole(promise: Promise) {
    if (GatewayDialerSupport.isDefaultDialer(reactContext)) {
      promise.resolve(true)
      return
    }

    pendingDialerRolePromise = promise
    val launched =
      GatewayDialerSupport.launchRolePrompt(
        reactContext,
        reactApplicationContext.currentActivity,
        REQUEST_DIALER_ROLE,
      )
    if (!launched) {
      pendingDialerRolePromise = null
      promise.reject("NO_ACTIVITY", "An activity is required to request the dialer role.")
      return
    }
  }

  @ReactMethod
  fun getDialerStatus(promise: Promise) {
    promise.resolve(JsonBridge.toWritableMap(GatewayDialerManager.getDialerStatus(reactContext)))
  }

  @ReactMethod
  fun startGateway(configMap: ReadableMap, promise: Promise) {
    if (!GatewayStatusFactory.isDefaultSmsApp(reactContext)) {
      promise.reject(
        "ROLE_SMS_REQUIRED",
        "App must be the default SMS handler before the gateway can start.",
      )
      return
    }
    if (!GatewayPermissions.smsPermissionsGranted(reactContext)) {
      promise.reject(
        "SMS_PERMISSIONS_REQUIRED",
        "SMS, receive, send, and phone-state permissions are required before the gateway can start.",
      )
      return
    }

    val host = configMap.getString("host") ?: "0.0.0.0"
    val port = configMap.getInt("port")
    val suppliedApiKey = if (configMap.hasKey("apiKey")) configMap.getString("apiKey") else null
    val configStore = GatewayConfigStore(reactContext)
    var nextConfig = configStore.load()
    var generatedApiKey: String? = null

    if (!suppliedApiKey.isNullOrBlank()) {
      configStore.storeApiKey(suppliedApiKey)
    } else if (!configStore.hasApiKey()) {
      generatedApiKey = configStore.generateApiKey()
      configStore.storeApiKey(generatedApiKey)
    }

    nextConfig =
      nextConfig.copy(
        enabled = true,
        host = host,
        port = port,
        apiKeyHash = configStore.load().apiKeyHash,
        apiKeyPreview = configStore.load().apiKeyPreview,
      )
    configStore.save(nextConfig)

    try {
      val startContext = reactApplicationContext.currentActivity ?: reactApplicationContext
      Log.i(TAG, "Starting gateway service using ${startContext::class.java.simpleName}")
      GatewayForegroundService.ensureStarted(startContext)
      SmsGatewayHealthWorker.schedule(reactContext)

      promise.resolve(
        JsonBridge.toWritableMap(
          JSONObject()
            .put("generatedApiKey", generatedApiKey ?: JSONObject.NULL)
            .put("status", GatewayStatusFactory.create(reactContext)),
        ),
      )
    } catch (error: Exception) {
      configStore.setEnabled(false)
      Log.e(TAG, "Unable to start gateway", error)
      promise.reject(
        "GATEWAY_START_FAILED",
        error.message ?: "Unable to start gateway foreground service.",
        error,
      )
    }
  }

  @ReactMethod
  fun stopGateway(promise: Promise) {
    GatewayConfigStore(reactContext).setEnabled(false)
    SmsGatewayHealthWorker.cancel(reactContext)
    GatewayForegroundService.stop(reactContext)
    promise.resolve(JsonBridge.toWritableMap(GatewayStatusFactory.create(reactContext)))
  }

  @ReactMethod
  fun generateApiKey(promise: Promise) {
    val store = GatewayConfigStore(reactContext)
    val apiKey = store.generateApiKey()
    store.storeApiKey(apiKey)
    promise.resolve(
      JsonBridge.toWritableMap(
        JSONObject()
          .put("apiKey", apiKey)
          .put("status", GatewayStatusFactory.create(reactContext)),
      ),
    )
  }

  @ReactMethod
  fun listSubscriptions(promise: Promise) {
    promise.resolve(JsonBridge.toWritableArray(GatewayStatusFactory.listSubscriptions(reactContext)))
  }

  @ReactMethod
  fun listRecentCalls(limit: Int, promise: Promise) {
    promise.resolve(
      JsonBridge.toWritableArray(GatewayDialerManager.listRecentCalls(reactContext, limit)),
    )
  }

  @ReactMethod
  fun listConversations(limit: Int, promise: Promise) {
    promise.resolve(
      JsonBridge.toWritableArray(SmsConversationRepository(reactContext).listConversations(limit)),
    )
  }

  @ReactMethod
  fun getConversationMessages(request: ReadableMap, promise: Promise) {
    val threadId =
      if (request.hasKey("threadId")) request.getString("threadId") else null
    val address =
      if (request.hasKey("address")) request.getString("address") else null
    val limit = if (request.hasKey("limit")) request.getInt("limit") else 200

    promise.resolve(
      JsonBridge.toWritableArray(
        SmsConversationRepository(reactContext).getMessages(threadId, address, limit),
      ),
    )
  }

  @ReactMethod
  fun markConversationRead(request: ReadableMap, promise: Promise) {
    if (!GatewayStatusFactory.isDefaultSmsApp(reactContext)) {
      promise.reject(
        "ROLE_SMS_REQUIRED",
        "App must be the default SMS handler before updating conversation state.",
      )
      return
    }

    val threadId = if (request.hasKey("threadId")) request.getString("threadId") else null
    val address = if (request.hasKey("address")) request.getString("address") else null

    promise.resolve(
      SmsConversationRepository(reactContext).markConversationRead(threadId, address),
    )
  }

  @ReactMethod
  fun markConversationUnread(request: ReadableMap, promise: Promise) {
    if (!GatewayStatusFactory.isDefaultSmsApp(reactContext)) {
      promise.reject(
        "ROLE_SMS_REQUIRED",
        "App must be the default SMS handler before updating conversation state.",
      )
      return
    }

    val threadId = if (request.hasKey("threadId")) request.getString("threadId") else null
    val address = if (request.hasKey("address")) request.getString("address") else null

    promise.resolve(
      SmsConversationRepository(reactContext).markConversationUnread(threadId, address),
    )
  }

  @ReactMethod
  fun deleteConversation(request: ReadableMap, promise: Promise) {
    if (!GatewayStatusFactory.isDefaultSmsApp(reactContext)) {
      promise.reject(
        "ROLE_SMS_REQUIRED",
        "App must be the default SMS handler before deleting conversations.",
      )
      return
    }

    val threadId = if (request.hasKey("threadId")) request.getString("threadId") else null
    val address = if (request.hasKey("address")) request.getString("address") else null

    promise.resolve(
      SmsConversationRepository(reactContext).deleteConversation(threadId, address),
    )
  }

  @ReactMethod
  fun sendSmsMessage(request: ReadableMap, promise: Promise) {
    if (!GatewayStatusFactory.isDefaultSmsApp(reactContext)) {
      promise.reject(
        "ROLE_SMS_REQUIRED",
        "App must be the default SMS handler before sending SMS messages.",
      )
      return
    }
    if (!GatewayPermissions.smsPermissionsGranted(reactContext)) {
      promise.reject(
        "SMS_PERMISSIONS_REQUIRED",
        "SMS, receive, send, and phone-state permissions are required before sending SMS messages.",
      )
      return
    }

    val destination = request.getString("destination")?.trim().orEmpty()
    val body = request.getString("body")?.trim().orEmpty()
    if (destination.isBlank() || body.isBlank()) {
      promise.reject("INVALID_SMS", "destination and body are required.")
      return
    }

    val subscriptionId =
      if (request.hasKey("subscriptionId")) request.getInt("subscriptionId") else null

    promise.resolve(
      JsonBridge.toWritableMap(
        SmsGatewayCore.enqueueOutboundSms(
          reactContext,
          destination,
          body,
          subscriptionId,
        ),
      ),
    )
  }

  @ReactMethod
  fun sendMmsMessage(request: ReadableMap, promise: Promise) {
    if (!GatewayStatusFactory.isDefaultSmsApp(reactContext)) {
      promise.reject(
        "ROLE_SMS_REQUIRED",
        "App must be the default SMS handler before sending MMS messages.",
      )
      return
    }
    if (!GatewayPermissions.smsPermissionsGranted(reactContext)) {
      promise.reject(
        "SMS_PERMISSIONS_REQUIRED",
        "SMS, receive, send, and phone-state permissions are required before sending MMS messages.",
      )
      return
    }

    val destination = request.getString("destination")?.trim().orEmpty()
    val body =
      if (request.hasKey("body")) request.getString("body")?.trim().orEmpty() else ""
    val subject =
      if (request.hasKey("subject")) request.getString("subject")?.trim() else null
    if (destination.isBlank()) {
      promise.reject("INVALID_MMS", "destination is required.")
      return
    }
    if (!request.hasKey("attachment")) {
      promise.reject("INVALID_MMS", "attachment is required.")
      return
    }

    val subscriptionId =
      if (request.hasKey("subscriptionId")) request.getInt("subscriptionId") else null

    try {
      val attachment = JsonBridge.readableMapToJson(request.getMap("attachment")!!)
      val result =
        SmsGatewayCore.enqueueOutboundMms(
          reactContext,
          destination,
          body,
          subject,
          attachment,
          subscriptionId,
        )
      promise.resolve(
        JsonBridge.toWritableMap(
          GatewayEventSanitizer.sanitizePayload(result),
        ),
      )
    } catch (error: Exception) {
      promise.reject("MMS_SEND_FAILED", error.message, error)
    }
  }

  @ReactMethod
  fun pickMmsAttachment(promise: Promise) {
    val activity = reactApplicationContext.currentActivity
    if (activity == null) {
      promise.reject("NO_ACTIVITY", "An activity is required to pick an attachment.")
      return
    }

    pendingAttachmentPromise?.reject(
      "ATTACHMENT_IN_PROGRESS",
      "Another attachment picker request is already active.",
    )
    pendingAttachmentPromise = promise

    val intent =
      Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, MmsSupport.supportedMimeTypes())
      }

    activity.startActivityForResult(intent, REQUEST_ATTACHMENT_PICK)
  }

  @ReactMethod
  fun openBatteryOptimizationSettings(promise: Promise) {
    val powerManager = reactContext.getSystemService(PowerManager::class.java)
    if (powerManager.isIgnoringBatteryOptimizations(reactContext.packageName)) {
      promise.resolve(true)
      return
    }

    val intent =
      Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${reactContext.packageName}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
    reactContext.startActivity(intent)
    promise.resolve(true)
  }

  @ReactMethod
  fun placeCall(request: ReadableMap, promise: Promise) {
    val number = request.getString("number")?.trim().orEmpty()
    val speakerphone =
      if (request.hasKey("speakerphone")) request.getBoolean("speakerphone") else false

    if (number.isBlank()) {
      promise.reject("INVALID_CALL", "number is required.")
      return
    }

    try {
      promise.resolve(
        JsonBridge.toWritableMap(
          GatewayDialerManager.placeCall(reactContext, number, speakerphone),
        ),
      )
    } catch (error: Exception) {
      promise.reject("CALL_PLACE_FAILED", error.message, error)
    }
  }

  @ReactMethod
  fun answerCall(request: ReadableMap, promise: Promise) {
    val callId = request.getString("callId")?.trim().orEmpty()
    if (callId.isBlank()) {
      promise.reject("INVALID_CALL", "callId is required.")
      return
    }

    try {
      promise.resolve(GatewayDialerManager.answerCall(reactContext, callId))
    } catch (error: Exception) {
      promise.reject("CALL_ANSWER_FAILED", error.message, error)
    }
  }

  @ReactMethod
  fun rejectCall(request: ReadableMap, promise: Promise) {
    val callId = request.getString("callId")?.trim().orEmpty()
    if (callId.isBlank()) {
      promise.reject("INVALID_CALL", "callId is required.")
      return
    }

    try {
      promise.resolve(GatewayDialerManager.rejectCall(reactContext, callId))
    } catch (error: Exception) {
      promise.reject("CALL_REJECT_FAILED", error.message, error)
    }
  }

  @ReactMethod
  fun endCall(request: ReadableMap, promise: Promise) {
    val callId = request.getString("callId")?.trim().orEmpty()
    if (callId.isBlank()) {
      promise.reject("INVALID_CALL", "callId is required.")
      return
    }

    try {
      promise.resolve(GatewayDialerManager.endCall(reactContext, callId))
    } catch (error: Exception) {
      promise.reject("CALL_END_FAILED", error.message, error)
    }
  }

  @ReactMethod
  fun setMuted(request: ReadableMap, promise: Promise) {
    val callId = request.getString("callId")?.trim().orEmpty()
    if (callId.isBlank() || !request.hasKey("muted")) {
      promise.reject("INVALID_CALL", "callId and muted are required.")
      return
    }

    try {
      promise.resolve(
        GatewayDialerManager.setMuted(reactContext, callId, request.getBoolean("muted")),
      )
    } catch (error: Exception) {
      promise.reject("CALL_MUTE_FAILED", error.message, error)
    }
  }

  @ReactMethod
  fun showInCallScreen(request: ReadableMap, promise: Promise) {
    val showDialpad =
      if (request.hasKey("showDialpad")) request.getBoolean("showDialpad") else false

    try {
      promise.resolve(GatewayDialerManager.showInCallScreen(reactContext, showDialpad))
    } catch (error: Exception) {
      promise.reject("CALL_UI_FAILED", error.message, error)
    }
  }

  @ReactMethod
  fun addListener(eventName: String) = Unit

  @ReactMethod
  fun removeListeners(count: Int) = Unit

  companion object {
    private const val TAG = "SmsGatewayModule"
    private const val REQUEST_SMS_ROLE = 8788
    private const val REQUEST_ATTACHMENT_PICK = 8789
    private const val REQUEST_DIALER_ROLE = 8790
  }
}
