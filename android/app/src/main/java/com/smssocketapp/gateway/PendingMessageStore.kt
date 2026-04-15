package com.smssocketapp.gateway

import android.content.Context
import org.json.JSONObject

class PendingMessageStore(context: Context) {
  private val preferences =
    context.getSharedPreferences(GatewayConfigStore.PREFS_NAME, Context.MODE_PRIVATE)

  fun put(messageId: String, payload: JSONObject) {
    preferences.edit().putString(keyFor(messageId), payload.toString()).apply()
  }

  fun get(messageId: String): JSONObject? =
    preferences.getString(keyFor(messageId), null)?.let(::JSONObject)

  fun remove(messageId: String) {
    preferences.edit().remove(keyFor(messageId)).apply()
  }

  private fun keyFor(messageId: String): String = "pending_$messageId"
}
