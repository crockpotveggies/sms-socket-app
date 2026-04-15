package com.smssocketapp.gateway

import android.content.Context
import android.provider.BaseColumns
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

class SmsHistoryRepository(private val context: Context) {
  fun rehydrateSince(since: Long, limit: Int): JSONArray {
    val events = JSONArray()
    val boundedLimit = limit.coerceIn(1, 500)
    val sortOrder = "${Telephony.TextBasedSmsColumns.DATE} ASC LIMIT $boundedLimit"
    val selection = "${Telephony.TextBasedSmsColumns.DATE} > ?"
    val selectionArgs = arrayOf(since.toString())
    val projection =
      arrayOf(
        BaseColumns._ID,
        Telephony.TextBasedSmsColumns.ADDRESS,
        Telephony.TextBasedSmsColumns.BODY,
        Telephony.TextBasedSmsColumns.DATE,
        Telephony.TextBasedSmsColumns.TYPE,
        "sub_id",
        Telephony.TextBasedSmsColumns.STATUS,
        Telephony.TextBasedSmsColumns.ERROR_CODE,
        Telephony.TextBasedSmsColumns.READ,
      )

    context.contentResolver.query(
      Telephony.Sms.CONTENT_URI,
      projection,
      selection,
      selectionArgs,
      sortOrder,
    )?.use { cursor ->
      val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
      val addressIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.ADDRESS)
      val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.BODY)
      val dateIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.DATE)
      val typeIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.TYPE)
      val subIdIndex = cursor.getColumnIndex("sub_id")
      val statusIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.STATUS)
      val errorIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.ERROR_CODE)
      val readIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.READ)

      while (cursor.moveToNext()) {
        val type = cursor.getInt(typeIndex)
        val eventType =
          when (type) {
            Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX -> "sms.received"
            Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT -> "sms.outbound.sent"
            Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED -> "sms.outbound.failed"
            Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX -> "sms.outbound.accepted"
            else -> "sms.history"
          }

        events.put(
          JSONObject()
            .put("id", "history-${cursor.getLong(idIndex)}")
            .put("type", eventType)
            .put("timestamp", cursor.getLong(dateIndex))
            .put(
              "payload",
              JSONObject()
                .put("address", cursor.getString(addressIndex))
                .put("body", cursor.getString(bodyIndex))
                .put(
                  "subscriptionId",
                  if (subIdIndex >= 0) cursor.getInt(subIdIndex) else JSONObject.NULL,
                )
                .put(
                  "status",
                  if (statusIndex >= 0) cursor.getInt(statusIndex) else JSONObject.NULL,
                )
                .put(
                  "errorCode",
                  if (errorIndex >= 0) cursor.getInt(errorIndex) else JSONObject.NULL,
                )
                .put(
                  "read",
                  if (readIndex >= 0) cursor.getInt(readIndex) == 1 else JSONObject.NULL,
                ),
            ),
        )
      }
    }

    return events
  }
}
