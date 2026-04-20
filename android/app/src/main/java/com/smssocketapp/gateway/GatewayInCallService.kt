package com.smssocketapp.gateway

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.InCallService
import com.smssocketapp.MainActivity

class GatewayInCallService : InCallService() {
  override fun onCreate() {
    super.onCreate()
    GatewayDialerManager.onServiceCreated(this)
  }

  override fun onDestroy() {
    GatewayDialerManager.onServiceDestroyed(this)
    super.onDestroy()
  }

  override fun onCallAdded(call: Call) {
    super.onCallAdded(call)
    GatewayDialerManager.onCallAdded(call)
  }

  override fun onCallRemoved(call: Call) {
    GatewayDialerManager.onCallRemoved(call)
    super.onCallRemoved(call)
  }

  override fun onCallAudioStateChanged(audioState: CallAudioState) {
    super.onCallAudioStateChanged(audioState)
    GatewayDialerManager.onAudioStateChanged(audioState)
  }

  override fun onBringToForeground(showDialpad: Boolean) {
    super.onBringToForeground(showDialpad)
    GatewayRuntime.recordEvent(
      "dialer.ui.requested",
      org.json.JSONObject().put("showDialpad", showDialpad),
    )
    startActivity(
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("openCalls", true)
        putExtra("showDialpad", showDialpad)
      },
    )
  }

  override fun onMuteStateChanged(isMuted: Boolean) {
    super.onMuteStateChanged(isMuted)
    GatewayDialerManager.onMuteStateChanged(isMuted)
  }

  override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
    super.onCallEndpointChanged(callEndpoint)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      GatewayDialerManager.onCallEndpointChanged(
        when (callEndpoint.endpointType) {
          CallEndpoint.TYPE_BLUETOOTH -> "bluetooth"
          CallEndpoint.TYPE_WIRED_HEADSET -> "wired_headset"
          CallEndpoint.TYPE_SPEAKER -> "speaker"
          CallEndpoint.TYPE_EARPIECE -> "earpiece"
          CallEndpoint.TYPE_STREAMING -> "streaming"
          else -> "unknown"
        },
      )
    }
  }
}
