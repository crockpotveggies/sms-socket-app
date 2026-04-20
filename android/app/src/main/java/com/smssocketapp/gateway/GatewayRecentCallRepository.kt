package com.smssocketapp.gateway

import android.content.Context
import android.provider.CallLog
import org.json.JSONArray

class GatewayRecentCallRepository(
  private val context: Context,
) {
  fun listRecentCalls(limit: Int): JSONArray {
    if (!GatewayPermissions.recentCallsPermissionGranted(context) || limit <= 0) {
      return JSONArray()
    }

    val results = JSONArray()
    val resolver = context.contentResolver
    val projection =
      arrayOf(
        CallLog.Calls._ID,
        CallLog.Calls.NUMBER,
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.TYPE,
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
      )

    try {
      resolver.query(
        CallLog.Calls.CONTENT_URI,
        projection,
        null,
        null,
        "${CallLog.Calls.DATE} DESC",
      )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
        val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
        val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
        val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)

        while (cursor.moveToNext() && results.length() < limit) {
          val number = cursor.getString(numberIndex).orEmpty()
          val displayName = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: number

          results.put(
            GatewayRecentCall(
              id = cursor.getLong(idIndex).toString(),
              number = number,
              displayName = displayName,
              direction = GatewayCallStateMapper.normalizeRecentCallDirection(cursor.getInt(typeIndex)),
              timestamp = cursor.getLong(dateIndex),
              durationSeconds = cursor.getLong(durationIndex),
            ).toJson(),
          )
        }
      }
    } catch (_: SecurityException) {
      return JSONArray()
    }

    return results
  }
}
