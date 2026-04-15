package com.smssocketapp.gateway

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class SmsConversationRepository(private val context: Context) {
  fun listConversations(limit: Int = 100): JSONArray {
    val conversations = JSONArray()
    val resolver = context.contentResolver
    val seenThreads = linkedSetOf<String>()
    val unreadCounts = mutableMapOf<String, Int>()
    val projection =
      arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.THREAD_ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.TYPE,
        Telephony.Sms.READ,
      )

    resolver.query(
      Telephony.Sms.Inbox.CONTENT_URI,
      arrayOf(Telephony.Sms.THREAD_ID),
      "${Telephony.Sms.READ} = 0",
      null,
      null,
    )?.use { unreadCursor ->
      val threadIdIndex = unreadCursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
      while (unreadCursor.moveToNext()) {
        val threadId = unreadCursor.getLong(threadIdIndex).toString()
        unreadCounts[threadId] = (unreadCounts[threadId] ?: 0) + 1
      }
    }

    resolver.query(
      Telephony.Sms.CONTENT_URI,
      projection,
      null,
      null,
      "${Telephony.Sms.DATE} DESC",
    )?.use { cursor ->
      val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
      val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
      val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
      val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
      val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
      val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
      val readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)

      while (cursor.moveToNext() && conversations.length() < limit) {
        val threadId = cursor.getLong(threadIdIndex).toString()
        if (!seenThreads.add(threadId)) {
          continue
        }

        conversations.put(
          decorateAddress(
          JSONObject()
            .put("id", cursor.getLong(idIndex).toString())
            .put("threadId", threadId)
            .put("address", cursor.getString(addressIndex).orEmpty())
            .put("snippet", cursor.getString(bodyIndex).orEmpty())
            .put("timestamp", cursor.getLong(dateIndex))
            .put("messageType", cursor.getInt(typeIndex))
            .put("read", cursor.getInt(readIndex) == 1),
          ).put("unreadCount", unreadCounts[threadId] ?: 0),
        )
      }
    }

    return conversations
  }

  fun getMessages(
    threadId: String? = null,
    address: String? = null,
    limit: Int = 200,
  ): JSONArray {
    val messages = mutableListOf<JSONObject>()
    val resolver = context.contentResolver
    val projection =
      arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.THREAD_ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.TYPE,
        Telephony.Sms.READ,
        Telephony.Sms.STATUS,
      )

    val selection: String?
    val selectionArgs: Array<String>?
    when {
      !threadId.isNullOrBlank() -> {
        selection = "${Telephony.Sms.THREAD_ID} = ?"
        selectionArgs = arrayOf(threadId)
      }
      !address.isNullOrBlank() -> {
        selection = "${Telephony.Sms.ADDRESS} = ?"
        selectionArgs = arrayOf(address)
      }
      else -> {
        selection = null
        selectionArgs = null
      }
    }

    resolver.query(
      Telephony.Sms.CONTENT_URI,
      projection,
      selection,
      selectionArgs,
      "${Telephony.Sms.DATE} DESC",
    )?.use { cursor ->
      val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
      val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
      val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
      val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
      val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
      val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
      val readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
      val statusIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)

      while (cursor.moveToNext() && messages.size < limit) {
        messages.add(
          decorateAddress(
          JSONObject()
            .put("id", cursor.getLong(idIndex).toString())
            .put("threadId", cursor.getLong(threadIdIndex).toString())
            .put("address", cursor.getString(addressIndex).orEmpty())
            .put("body", cursor.getString(bodyIndex).orEmpty())
            .put("timestamp", cursor.getLong(dateIndex))
            .put("messageType", cursor.getInt(typeIndex))
            .put("read", cursor.getInt(readIndex) == 1)
            .put("status", cursor.getInt(statusIndex)),
          ),
        )
      }
    }

    val ordered = JSONArray()
    messages.asReversed().forEach { message -> ordered.put(message) }
    return ordered
  }

  fun markConversationRead(threadId: String? = null, address: String? = null): Boolean {
    val selectionData = selectionForConversation(threadId, address) ?: return false
    val values = ContentValues().apply {
      put(Telephony.Sms.READ, 1)
      put(Telephony.Sms.SEEN, 1)
    }

    val updated =
      context.contentResolver.update(
        Telephony.Sms.CONTENT_URI,
        values,
        selectionData.first,
        selectionData.second,
      )

    return updated > 0
  }

  fun deleteConversation(threadId: String? = null, address: String? = null): Boolean {
    val selectionData = selectionForConversation(threadId, address) ?: return false
    val deleted =
      context.contentResolver.delete(
        Telephony.Sms.CONTENT_URI,
        selectionData.first,
        selectionData.second,
      )

    return deleted > 0
  }

  private fun selectionForConversation(
    threadId: String?,
    address: String?,
  ): Pair<String, Array<String>>? =
    when {
      !threadId.isNullOrBlank() -> {
        "${Telephony.Sms.THREAD_ID} = ?" to arrayOf(threadId)
      }
      !address.isNullOrBlank() -> {
        "${Telephony.Sms.ADDRESS} = ?" to arrayOf(address)
      }
      else -> null
    }

  private fun decorateAddress(payload: JSONObject): JSONObject {
    val address = payload.optString("address")
    payload.put("displayName", lookupDisplayName(address))
    payload.put("initials", initialsFor(payload.optString("displayName"), address))
    return payload
  }

  private fun lookupDisplayName(address: String): String {
    if (address.isBlank()) {
      return ""
    }
    if (
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      return ""
    }

    val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
      .appendPath(address)
      .build()

    context.contentResolver.query(
      uri,
      arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
      null,
      null,
      null,
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        return cursor.getString(
          cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME),
        ).orEmpty()
      }
    }

    return ""
  }

  private fun initialsFor(displayName: String, address: String): String {
    val source = if (displayName.isNotBlank()) displayName else address
    val tokens = source.split(" ").filter { it.isNotBlank() }
    return when {
      tokens.size >= 2 -> "${tokens.first().first()}${tokens.last().first()}".uppercase()
      source.length >= 2 -> source.takeLast(2)
      source.isNotBlank() -> source.take(1)
      else -> "?"
    }
  }
}
