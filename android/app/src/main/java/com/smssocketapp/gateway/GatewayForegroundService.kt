package com.smssocketapp.gateway

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.smssocketapp.MainActivity
import com.smssocketapp.R
import org.json.JSONObject
import java.lang.ref.WeakReference

class GatewayForegroundService : Service() {
  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    currentService = WeakReference(this)
    Log.i(TAG, "GatewayForegroundService created")
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action ?: ACTION_START
    Log.i(TAG, "onStartCommand action=$action startId=$startId")

    if (action == ACTION_STOP) {
      GatewayRuntime.recordEvent("gateway.event", JSONObject().put("message", "Gateway stopped"))
      GatewayRuntime.stopServer()
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }

    val config = GatewayConfigStore(this).load()
    if (!config.enabled) {
      Log.i(TAG, "Ignoring start because gateway is disabled in config")
      stopSelf()
      return START_NOT_STICKY
    }

    return try {
      startForeground(NOTIFICATION_ID, buildNotification())
      Log.i(TAG, "Foreground notification posted")
      GatewayRuntime.startServer(this, config)
      GatewayRuntime.recordEvent("gateway.event", JSONObject().put("message", "Gateway started"))
      SmsGatewayHealthWorker.schedule(this)
      refreshNotification()
      START_STICKY
    } catch (error: Exception) {
      Log.e(TAG, "Failed to start gateway foreground service", error)
      GatewayRuntime.recordEvent(
        "gateway.error",
        JSONObject()
          .put("message", "Foreground service failed to start")
          .put("error", error.message ?: error.javaClass.simpleName),
      )
      stopSelf()
      START_NOT_STICKY
    }
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)

    val config = GatewayConfigStore(this).load()
    if (!config.enabled) {
      return
    }

    val restartIntent =
      Intent(this, GatewayRestartReceiver::class.java).setAction(ACTION_RESTART)
    val pendingIntent =
      PendingIntent.getBroadcast(
        this,
        102,
        restartIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
    alarmManager.setAndAllowWhileIdle(
      AlarmManager.ELAPSED_REALTIME_WAKEUP,
      SystemClock.elapsedRealtime() + 3_000L,
      pendingIntent,
    )
  }

  override fun onDestroy() {
    Log.i(TAG, "GatewayForegroundService destroyed")
    currentService = null
    GatewayRuntime.stopServer()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  fun refreshNotification() {
    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(NOTIFICATION_ID, buildNotification())
  }

  private fun buildNotification(): Notification {
    val status = GatewayStatusFactory.create(this)
    val openAppIntent =
      PendingIntent.getActivity(
        this,
        100,
        Intent(this, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopIntent =
      PendingIntent.getService(
        this,
        101,
        Intent(this, GatewayForegroundService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(getString(R.string.gateway_notification_title))
      .setContentText(
        "Listening on ${status.optString("host")}:${status.optInt("port")} | ${status.optInt("connectionCount")} client(s)",
      )
      .setStyle(
        NotificationCompat.BigTextStyle().bigText(
          "Listening on ${status.optString("host")}:${status.optInt("port")} | ${status.optInt("connectionCount")} client(s)",
        ),
      )
      .setSmallIcon(R.drawable.ic_notification)
      .setColor(ContextCompat.getColor(this, R.color.ic_launcher_background))
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setContentIntent(openAppIntent)
      .addAction(0, getString(R.string.gateway_notification_stop), stopIntent)
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        getString(R.string.gateway_notification_channel_name),
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = getString(R.string.gateway_notification_channel_description)
      }
    manager.createNotificationChannel(channel)
  }

  companion object {
    private const val TAG = "GatewayForegroundSvc"
    private const val CHANNEL_ID = "sms_gateway"
    private const val NOTIFICATION_ID = 8787
    const val ACTION_START = "com.smssocketapp.gateway.START"
    const val ACTION_STOP = "com.smssocketapp.gateway.STOP"
    const val ACTION_RESTART = "com.smssocketapp.gateway.RESTART"

    private var currentService: WeakReference<GatewayForegroundService>? = null

    fun ensureStarted(context: Context) {
      val intent = Intent(context, GatewayForegroundService::class.java).setAction(ACTION_START)
      try {
        ContextCompat.startForegroundService(context, intent)
      } catch (error: Exception) {
        Log.e(TAG, "Unable to request foreground-service start", error)
        throw error
      }
    }

    fun stop(context: Context) {
      ContextCompat.startForegroundService(
        context,
        Intent(context, GatewayForegroundService::class.java).setAction(ACTION_STOP),
      )
    }

    fun refreshNotification() {
      currentService?.get()?.refreshNotification()
    }
  }
}
