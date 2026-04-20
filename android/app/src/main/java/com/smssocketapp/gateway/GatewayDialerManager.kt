package com.smssocketapp.gateway

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.IdentityHashMap

object GatewayDialerManager {
  private var applicationContext: Context? = null
  private var inCallServiceRef: WeakReference<GatewayInCallService>? = null
  private val callIds = StableIdRegistry<Call>()
  private val callsById = linkedMapOf<String, Call>()
  private val callbacks = IdentityHashMap<Call, Call.Callback>()
  private var currentRoute = "unknown"
  private var muted = false
  private var serviceHealthy = true

  fun initialize(context: Context) {
    applicationContext = context.applicationContext
  }

  @Synchronized
  fun onServiceCreated(service: GatewayInCallService) {
    inCallServiceRef = WeakReference(service)
    serviceHealthy = true
    emitDialerState()
  }

  @Synchronized
  fun onServiceDestroyed(service: GatewayInCallService) {
    if (inCallServiceRef?.get() == service) {
      inCallServiceRef = null
    }
    if (callsById.isNotEmpty()) {
      serviceHealthy = false
    }
    emitDialerState()
  }

  @Synchronized
  fun onCallAdded(call: Call) {
    val callId = callIds.idFor(call)
    Log.i(TAG, "call added callId=$callId state=${GatewayCallStateMapper.normalizeState(call.state)}")
    val callback =
      object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
          Log.i(
            TAG,
            "call state changed callId=${callIds.idFor(call)} state=${GatewayCallStateMapper.normalizeState(state)}",
          )
          emitCallEvent("call.updated", call)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
          Log.i(TAG, "call details changed callId=${callIds.idFor(call)}")
          emitCallEvent("call.updated", call)
        }

        override fun onChildrenChanged(call: Call, children: MutableList<Call>) {
          emitCallEvent("call.updated", call)
        }

        override fun onParentChanged(call: Call, parent: Call?) {
          emitCallEvent("call.updated", call)
        }
      }

    callbacks[call] = callback
    call.registerCallback(callback)
    callsById[callId] = call
    serviceHealthy = true
    emitCallEvent("call.added", call)
  }

  @Synchronized
  fun onCallRemoved(call: Call) {
    val snapshot = snapshotFor(call)
    Log.i(
      TAG,
      "call removed callId=${callIds.idFor(call)} state=${GatewayCallStateMapper.normalizeState(call.state)}",
    )
    callbacks.remove(call)?.let(call::unregisterCallback)
    callsById.remove(callIds.idFor(call))
    callIds.remove(call)
    if (snapshot != null) {
      GatewayRuntime.recordEvent("call.removed", snapshot.toJson())
    } else {
      emitDialerState()
    }
  }

  @Synchronized
  fun onAudioStateChanged(audioState: CallAudioState?) {
    currentRoute =
      if (audioState == null) {
        "unknown"
      } else {
        GatewayCallStateMapper.normalizeRoute(audioState.route)
      }
    muted = audioState?.isMuted ?: false

    callsById.values.forEach { call ->
      emitCallEvent("call.updated", call)
    }
    emitDialerState()
  }

  @Synchronized
  fun onMuteStateChanged(isMuted: Boolean) {
    muted = isMuted
    callsById.values.forEach { call ->
      emitCallEvent("call.updated", call)
    }
    emitDialerState()
  }

  @Synchronized
  fun onCallEndpointChanged(route: String) {
    currentRoute = route
    callsById.values.forEach { call ->
      emitCallEvent("call.updated", call)
    }
    emitDialerState()
  }

  @Synchronized
  fun getDialerStatus(context: Context): JSONObject =
    JSONObject().apply {
      val roleGranted = GatewayDialerSupport.isDefaultDialer(context)
      put("dialerRoleGranted", roleGranted)
      put("inCallServiceHealthy", roleGranted && serviceHealthy)
      put("activeCalls", if (roleGranted) activeCallsJson() else JSONArray())
      put("dialerMissingPermissions", JSONArray(GatewayPermissions.missingDialerPermissions(context)))
    }

  fun listRecentCalls(context: Context, limit: Int): JSONArray =
    GatewayRecentCallRepository(context).listRecentCalls(limit)

  fun placeCall(
    context: Context,
    number: String,
    speakerphone: Boolean,
  ): JSONObject {
    val telecomManager = context.getSystemService(TelecomManager::class.java)
      ?: throw IllegalStateException("Telecom service unavailable.")
    ensureDialerRole(context)
    ensureDialerPermissions(context)

    val sanitizedNumber = number.trim()
    if (sanitizedNumber.isBlank()) {
      throw IllegalArgumentException("number is required.")
    }

    val extras =
      Bundle().apply {
        putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, speakerphone)
      }
    Log.i(TAG, "dialer placeCall number=$sanitizedNumber speakerphone=$speakerphone")
    telecomManager.placeCall(Uri.fromParts("tel", sanitizedNumber, null), extras)

    return JSONObject()
      .put("requested", true)
      .put("number", sanitizedNumber)
      .put("speakerphone", speakerphone)
  }

  fun answerCall(
    context: Context,
    callId: String,
  ): Boolean {
    ensureDialerRole(context)
    ensureAnswerPermissions(context)
    val call = requireCall(callId)
    Log.i(TAG, "dialer answerCall callId=$callId")
    call.answer(VideoProfile.STATE_AUDIO_ONLY)
    return true
  }

  fun rejectCall(
    context: Context,
    callId: String,
  ): Boolean {
    ensureDialerRole(context)
    val call = requireCall(callId)
    Log.i(TAG, "dialer rejectCall callId=$callId")
    call.reject(false, null)
    return true
  }

  fun endCall(
    context: Context,
    callId: String,
  ): Boolean {
    ensureDialerRole(context)
    val call = requireCall(callId)
    Log.i(TAG, "dialer endCall callId=$callId")
    call.disconnect()
    return true
  }

  fun setMuted(
    context: Context,
    callId: String,
    shouldMute: Boolean,
  ): Boolean {
    ensureDialerRole(context)
    requireCall(callId)
    val service = inCallServiceRef?.get() ?: throw IllegalStateException("In-call service unavailable.")
    Log.i(TAG, "dialer setMuted callId=$callId muted=$shouldMute")
    service.setMuted(shouldMute)
    muted = shouldMute
    emitDialerState()
    return true
  }

  fun sendDtmf(
    context: Context,
    callId: String,
    digits: String,
  ): JSONObject {
    ensureDialerRole(context)
    Log.i(TAG, "dialer sendDtmf callId=$callId digits=$digits")
    return dispatchDtmf(
      callId = callId,
      digits = digits,
      playerResolver = { requestedCallId -> CallDtmfTonePlayer(requireCall(requestedCallId)) },
    )
  }

  fun showInCallScreen(
    context: Context,
    showDialpad: Boolean,
  ): Boolean {
    ensureDialerRole(context)
    val telecomManager = context.getSystemService(TelecomManager::class.java)
      ?: throw IllegalStateException("Telecom service unavailable.")
    Log.i(TAG, "dialer showInCallScreen showDialpad=$showDialpad")
    telecomManager.showInCallScreen(showDialpad)
    return true
  }

  @Synchronized
  private fun emitCallEvent(type: String, call: Call) {
    val snapshot = snapshotFor(call) ?: return
    GatewayRuntime.recordEvent(type, snapshot.toJson())
  }

  @Synchronized
  private fun snapshotFor(call: Call): GatewayCallSnapshot? {
    val callId = callsById.entries.firstOrNull { it.value == call }?.key ?: return null
    val details = call.details
    val state = GatewayCallStateMapper.normalizeState(call.state)
    val number = details?.handle?.schemeSpecificPart?.orEmpty().orEmpty()
    val displayName =
      details?.callerDisplayName?.takeIf { it.isNotBlank() } ?: number
    val direction =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && details != null) {
        GatewayCallStateMapper.normalizeDirection(details.callDirection, state)
      } else if (state == "ringing") {
        "incoming"
      } else {
        "outgoing"
      }
    val isConference =
      details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true ||
        call.parent != null ||
        call.children.isNotEmpty()

    return GatewayCallStateMapper.toSnapshot(
      callId,
      GatewayCallDescriptor(
        number = number,
        displayName = displayName,
        direction = direction,
        state = state,
        isMuted = muted,
        route = currentRoute,
        isConference = isConference,
      ),
    )
  }

  @Synchronized
  private fun activeCallsJson(): JSONArray {
    val calls = JSONArray()
    callsById.values.forEach { call ->
      snapshotFor(call)?.let { snapshot ->
        calls.put(snapshot.toJson())
      }
    }
    return calls
  }

  private fun emitDialerState() {
    val context = applicationContext ?: return
    GatewayRuntime.recordEvent("dialer.state", getDialerStatus(context))
  }

  internal fun dispatchDtmf(
    callId: String,
    digits: String,
    playerResolver: (String) -> GatewayDtmfTonePlayer,
    sequenceSender: (String, GatewayDtmfTonePlayer) -> Unit = { normalizedDigits, player ->
      GatewayDtmf.playSequence(normalizedDigits, player)
    },
  ): JSONObject {
    val normalizedCallId = callId.trim()
    if (normalizedCallId.isBlank()) {
      throw IllegalArgumentException("callId is required")
    }

    val normalizedDigits = GatewayDtmf.normalizeDigits(digits)
    val player = playerResolver(normalizedCallId)
    sequenceSender(normalizedDigits, player)
    return JSONObject()
      .put("sent", true)
      .put("callId", normalizedCallId)
      .put("digits", normalizedDigits)
  }

  @Synchronized
  private fun requireCall(callId: String): Call =
    callsById[callId] ?: throw IllegalArgumentException("Unknown callId: $callId")

  private fun ensureDialerRole(context: Context) {
    if (!GatewayDialerSupport.isDefaultDialer(context)) {
      throw IllegalStateException("App must be the default dialer before call control is available.")
    }
  }

  private fun ensureDialerPermissions(context: Context) {
    val missing = GatewayPermissions.missingDialerControlPermissions(context)
    if (missing.isNotEmpty()) {
      throw IllegalStateException(
        "Dialer permissions are required before placing or controlling calls: ${missing.joinToString()}",
      )
    }
  }

  private fun ensureAnswerPermissions(context: Context) {
    val missing = GatewayPermissions.missingDialerControlPermissions(context)
      .filterNot { it == android.Manifest.permission.CALL_PHONE }
    if (missing.isNotEmpty()) {
      throw IllegalStateException(
        "Answer-call permissions are required before controlling calls: ${missing.joinToString()}",
      )
    }
  }

  private class CallDtmfTonePlayer(
    private val call: Call,
  ) : GatewayDtmfTonePlayer {
    override fun playTone(digit: Char) {
      call.playDtmfTone(digit)
    }

    override fun stopTone() {
      call.stopDtmfTone()
    }
  }

  private const val TAG = "GatewayDialer"
}
