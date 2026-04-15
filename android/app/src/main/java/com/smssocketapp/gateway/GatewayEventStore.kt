package com.smssocketapp.gateway

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class GatewayEventStore(context: Context) {
  private val preferences =
    context.getSharedPreferences(GatewayConfigStore.PREFS_NAME, Context.MODE_PRIVATE)

  fun append(event: JSONObject) {
    val current = readAll()
    current.put(event)

    while (current.length() > MAX_EVENTS) {
      current.remove(0)
    }

    preferences.edit().putString(KEY_RECENT_EVENTS, current.toString()).apply()
  }

  fun getRecent(limit: Int = 50): JSONArray {
    val all = readAll()
    val start = maxOf(0, all.length() - limit)
    val result = JSONArray()
    for (index in all.length() - 1 downTo start) {
      result.put(all.getJSONObject(index))
    }
    return result
  }

  fun getSince(since: Long, limit: Int = 100): JSONArray {
    val result = JSONArray()
    val all = readAll()

    for (index in 0 until all.length()) {
      val event = all.getJSONObject(index)
      if (event.optLong("timestamp") > since) {
        result.put(event)
      }
      if (result.length() >= limit) {
        break
      }
    }

    return result
  }

  private fun readAll(): JSONArray =
    preferences.getString(KEY_RECENT_EVENTS, null)?.let(::JSONArray) ?: JSONArray()

  companion object {
    private const val KEY_RECENT_EVENTS = "recentEvents"
    private const val MAX_EVENTS = 200
  }
}
