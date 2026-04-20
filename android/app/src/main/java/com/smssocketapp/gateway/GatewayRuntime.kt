package com.smssocketapp.gateway

import android.app.Activity
import android.content.Context
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.InetSocketAddress
import java.util.UUID

object GatewayRuntime {
  private const val TAG = "GatewayRuntime"

  private var applicationContext: Context? = null
  private var reactContextRef: WeakReference<ReactApplicationContext>? = null
  private var server: GatewayWebSocketServer? = null
  private var openConnections: Int = 0

  fun initialize(context: Context) {
    applicationContext = context.applicationContext
  }

  fun bindReactContext(reactContext: ReactApplicationContext) {
    reactContextRef = WeakReference(reactContext)
    emitState()
  }

  fun unbindReactContext() {
    reactContextRef = null
  }

  fun isRunning(): Boolean = server != null

  fun connectionCount(): Int = openConnections

  fun currentActivity(): Activity? = reactContextRef?.get()?.currentActivity

  @Synchronized
  fun startServer(context: Context, config: GatewayConfig) {
    if (server != null) {
      stopServer()
    }

    val socketServer =
      GatewayWebSocketServer(
        context = context.applicationContext,
        address = InetSocketAddress(config.host, config.port),
        authValidator = { apiKey -> GatewayConfigStore(context).validateApiKey(apiKey) },
        onConnectionChanged = { count ->
          openConnections = count
          emitState()
          GatewayForegroundService.refreshNotification()
        },
        onEvent = { type, payload -> recordEvent(type, payload) },
      )

    try {
      socketServer.start()
      server = socketServer
      emitState()
    } catch (error: Exception) {
      Log.e(TAG, "Failed to start WebSocket server", error)
      throw error
    }
  }

  @Synchronized
  fun stopServer() {
    server?.stop(1000)
    server = null
    openConnections = 0
    emitState()
  }

  fun recordEvent(type: String, payload: JSONObject): JSONObject {
    val context = applicationContext ?: throw IllegalStateException("GatewayRuntime not initialized")
    val sanitizedPayload = GatewayEventSanitizer.sanitizePayload(payload)
    val timestamp = System.currentTimeMillis()
    val event =
      JSONObject()
        .put("id", UUID.randomUUID().toString())
        .put("type", type)
        .put("timestamp", timestamp)
        .put("payload", sanitizedPayload)

    GatewayEventStore(context).append(event)
    server?.broadcastGatewayEvent(type, payload, timestamp)
    emitReactEvent("SmsGatewayEvent", event)
    emitState()
    return event
  }

  fun emitState() {
    val context = applicationContext ?: return
    val status = GatewayStatusFactory.create(context)
    emitReactEvent("SmsGatewayState", status)
    server?.broadcastGatewayEvent("gateway.state", status, System.currentTimeMillis())
  }

  private fun emitReactEvent(name: String, payload: JSONObject) {
    reactContextRef?.get()?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)?.emit(
      name,
      JsonBridge.toWritableMap(payload),
    )
  }
}
