package com.smssocketapp.gateway

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.BaseColumns
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class GatewayMessageRepository(private val context: Context) {
  private data class GatewayMessage(
    val id: String,
    val kind: String,
    val threadId: String,
    val address: String,
    val participants: List<String>,
    val displayName: String,
    val initials: String,
    val body: String,
    val subject: String?,
    val timestamp: Long,
    val messageType: Int,
    val read: Boolean,
    val status: Int?,
    val deliveryState: String?,
    val carrierAccepted: Boolean?,
    val failureReason: String?,
    val hasMedia: Boolean,
    val attachments: JSONArray,
    val rawEventType: String?,
  )

  fun listConversations(limit: Int = 100): JSONArray {
    val merged = loadRecentMessages(limit.coerceAtLeast(100) * 4)
    val unreadCounts = loadUnreadCounts()
    val conversations = JSONArray()
    val seenThreads = linkedSetOf<String>()

    merged.forEach { message ->
      if (!seenThreads.add(message.threadId)) {
        return@forEach
      }
      if (conversations.length() >= limit) {
        return@forEach
      }

      conversations.put(
        JSONObject()
          .put("id", message.id)
          .put("kind", message.kind)
          .put("threadId", message.threadId)
          .put("address", message.address)
          .put("participants", JSONArray(message.participants))
          .put("displayName", message.displayName)
          .put("initials", message.initials)
          .put("snippet", conversationSnippet(message))
          .put("timestamp", message.timestamp)
          .put("messageType", message.messageType)
          .put("read", message.read)
          .put("unreadCount", unreadCounts[message.threadId] ?: 0)
          .put("subject", message.subject ?: JSONObject.NULL)
          .put("status", message.status ?: JSONObject.NULL)
          .put("deliveryState", message.deliveryState ?: JSONObject.NULL)
          .put("carrierAccepted", message.carrierAccepted ?: JSONObject.NULL)
          .put("failureReason", message.failureReason ?: JSONObject.NULL)
          .put("hasMedia", message.hasMedia),
      )
    }

    return conversations
  }

  fun getMessages(
    threadId: String? = null,
    address: String? = null,
    limit: Int = 200,
  ): JSONArray {
    val requestedThreadIds = resolveThreadIds(threadId, address)
    val merged =
      loadRecentMessages(limit.coerceAtLeast(200) * 4).filter { message ->
        when {
          requestedThreadIds.isNotEmpty() -> requestedThreadIds.contains(message.threadId)
          !address.isNullOrBlank() ->
            message.address == address || message.participants.any { it == address }
          else -> false
        }
      }

    val ordered = JSONArray()
    merged.sortedBy { it.timestamp }.takeLast(limit).forEach { message ->
      ordered.put(messageToJson(message))
    }
    return ordered
  }

  fun rehydrateSince(since: Long, limit: Int): JSONArray {
    val ordered = JSONArray()
    loadRecentMessages(limit.coerceIn(1, 500) * 6)
      .filter { message -> message.timestamp > since }
      .sortedBy { it.timestamp }
      .take(limit.coerceIn(1, 500))
      .forEach { message ->
        ordered.put(
          JSONObject()
            .put("id", "history-${message.id}")
            .put("type", message.rawEventType ?: historyEventType(message))
            .put("timestamp", message.timestamp)
            .put("payload", eventPayload(message)),
        )
      }

    return ordered
  }

  fun markConversationRead(threadId: String? = null, address: String? = null): Boolean =
    updateConversationState(threadId, address, true)

  fun markConversationUnread(threadId: String? = null, address: String? = null): Boolean =
    updateConversationState(threadId, address, false)

  fun deleteConversation(threadId: String? = null, address: String? = null): Boolean {
    val threadIds = resolveThreadIds(threadId, address)
    var deleted = false

    threadIds.forEach { resolvedThreadId ->
      val uri = Uri.parse("content://mms-sms/conversations/$resolvedThreadId")
      deleted = context.contentResolver.delete(uri, null, null) > 0 || deleted
    }

    return deleted
  }

  fun findMessageByUri(uri: Uri): JSONObject? {
    return when {
      uri.toString().startsWith(Telephony.Sms.CONTENT_URI.toString()) ->
        loadSmsMessages(50).firstOrNull { message ->
          uri.lastPathSegment == message.id.substringAfter("sms-")
        }?.let(::messageToJson)
      uri.toString().startsWith(Telephony.Mms.CONTENT_URI.toString()) ->
        loadMmsMessages(50).firstOrNull { message ->
          uri.lastPathSegment == message.id.substringAfter("mms-")
        }?.let(::messageToJson)
      else -> null
    }
  }

  fun findRecentMmsEvent(afterTimestampMs: Long): JSONObject? {
    val candidate =
      loadMmsMessages(12)
        .filter { it.timestamp >= afterTimestampMs - 60_000L }
        .maxByOrNull { it.timestamp }
        ?: return null

    return JSONObject()
      .put("type", candidate.rawEventType ?: historyEventType(candidate))
      .put("payload", eventPayload(candidate))
  }

  private fun updateConversationState(
    threadId: String?,
    address: String?,
    read: Boolean,
  ): Boolean {
    val threadIds = resolveThreadIds(threadId, address)
    if (threadIds.isEmpty()) {
      return false
    }

    val values =
      ContentValues().apply {
        put(Telephony.Sms.READ, if (read) 1 else 0)
        put(Telephony.Sms.SEEN, if (read) 1 else 0)
      }

    var updated = false
    threadIds.forEach { resolvedThreadId ->
      val selection = "${Telephony.Sms.THREAD_ID} = ?"
      val args = arrayOf(resolvedThreadId)
      updated = context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, selection, args) > 0 || updated
      updated = context.contentResolver.update(Telephony.Mms.CONTENT_URI, values, selection, args) > 0 || updated
    }

    return updated
  }

  private fun resolveThreadIds(threadId: String?, address: String?): Set<String> {
    if (!threadId.isNullOrBlank()) {
      return setOf(threadId)
    }

    if (address.isNullOrBlank()) {
      return emptySet()
    }

    val threadIds = linkedSetOf<String>()
    loadRecentMessages(600).forEach { message ->
      if (message.address == address || message.participants.any { it == address }) {
        threadIds.add(message.threadId)
      }
    }
    return threadIds
  }

  private fun loadRecentMessages(limit: Int): List<GatewayMessage> =
    (loadSmsMessages(limit) + loadMmsMessages(limit)).sortedByDescending { it.timestamp }

  private fun loadSmsMessages(limit: Int): List<GatewayMessage> {
    val messages = mutableListOf<GatewayMessage>()
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

    context.contentResolver.query(
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
      val statusIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)

      while (cursor.moveToNext() && messages.size < limit) {
        val address = cursor.getString(addressIndex).orEmpty()
        val displayName = lookupDisplayName(address)
        messages.add(
          GatewayMessage(
            id = "sms-${cursor.getLong(idIndex)}",
            kind = "sms",
            threadId = cursor.getLong(threadIdIndex).toString(),
            address = address,
            participants = listOf(address).filter { it.isNotBlank() },
            displayName = displayName,
            initials = initialsFor(displayName, address),
            body = cursor.getString(bodyIndex).orEmpty(),
            subject = null,
            timestamp = cursor.getLong(dateIndex),
            messageType = cursor.getInt(typeIndex),
            read = cursor.getInt(readIndex) == 1,
            status = cursor.getInt(statusIndex),
            deliveryState = smsDeliveryState(cursor.getInt(typeIndex), cursor.getInt(statusIndex)),
            carrierAccepted = null,
            failureReason = smsFailureReason(cursor.getInt(typeIndex), cursor.getInt(statusIndex)),
            hasMedia = false,
            attachments = MmsSupport.emptyAttachments(),
            rawEventType = null,
          ),
        )
      }
    }

    return messages
  }

  private fun loadMmsMessages(limit: Int): List<GatewayMessage> {
    val messages = mutableListOf<GatewayMessage>()
    val projection =
      arrayOf(
        BaseColumns._ID,
        Telephony.Mms.THREAD_ID,
        Telephony.Mms.DATE,
        Telephony.Mms.MESSAGE_BOX,
        Telephony.Mms.READ,
        Telephony.Mms.SUBJECT,
        Telephony.Mms.DATE_SENT,
        Telephony.Mms.RESPONSE_STATUS,
        Telephony.Mms.RESPONSE_TEXT,
        Telephony.Mms.STATUS,
        Telephony.Mms.RETRIEVE_STATUS,
        Telephony.Mms.RETRIEVE_TEXT,
        "sub_cs",
        "m_type",
      )

    context.contentResolver.query(
      Telephony.Mms.CONTENT_URI,
      projection,
      null,
      null,
      "${Telephony.Mms.DATE} DESC",
    )?.use { cursor ->
      val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
      val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
      val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)
      val messageBoxIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
      val readIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.READ)
      val subjectIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.SUBJECT)
      val dateSentIndex = cursor.getColumnIndex(Telephony.Mms.DATE_SENT)
      val responseStatusIndex = cursor.getColumnIndex(Telephony.Mms.RESPONSE_STATUS)
      val responseTextIndex = cursor.getColumnIndex(Telephony.Mms.RESPONSE_TEXT)
      val statusIndex = cursor.getColumnIndex(Telephony.Mms.STATUS)
      val retrieveStatusIndex = cursor.getColumnIndex(Telephony.Mms.RETRIEVE_STATUS)
      val retrieveTextIndex = cursor.getColumnIndex(Telephony.Mms.RETRIEVE_TEXT)
      val subjectCharsetIndex = cursor.getColumnIndex("sub_cs")
      val messageTypeIndex = cursor.getColumnIndex("m_type")

      while (cursor.moveToNext() && messages.size < limit) {
        val messageId = cursor.getLong(idIndex)
        val participants = loadMmsParticipants(messageId)
        val address = participants.firstOrNull().orEmpty()
        val displayName = lookupDisplayName(address)
        val bodyAndAttachments = loadMmsBodyAndAttachments(messageId)
        val subject =
          decodeSubject(
            cursor.getString(subjectIndex),
            if (subjectCharsetIndex >= 0) cursor.getInt(subjectCharsetIndex) else null,
          )
        val box = cursor.getInt(messageBoxIndex)
        val mType = if (messageTypeIndex >= 0) cursor.getInt(messageTypeIndex) else null
        val timestampMs = mmsTimestamp(cursor, dateIndex, dateSentIndex)
        val hasPdfAttachment = attachmentsContainMimeType(bodyAndAttachments.second, "application/pdf")
        val responseStatus = nullableInt(cursor, responseStatusIndex)
        val responseText = nullableString(cursor, responseTextIndex)
        val status = nullableInt(cursor, statusIndex)
        val retrieveStatus = nullableInt(cursor, retrieveStatusIndex)
        val retrieveText = nullableString(cursor, retrieveTextIndex)
        val statusSummary =
          MmsStatusSupport.fromProvider(
            messageBox = box,
            messageType = mType,
            responseStatus = responseStatus,
            responseText = responseText,
            status = status,
            retrieveStatus = retrieveStatus,
            retrieveText = retrieveText,
            timestampMs = timestampMs,
            nowMs = System.currentTimeMillis(),
            hasPdfAttachment = hasPdfAttachment,
          )

        messages.add(
          GatewayMessage(
            id = "mms-$messageId",
            kind = "mms",
            threadId = cursor.getLong(threadIdIndex).toString(),
            address = address,
            participants = participants,
            displayName = if (participants.size > 1 && displayName.isBlank()) participants.joinToString(", ") else displayName,
            initials = initialsFor(displayName, address),
            body = bodyAndAttachments.first,
            subject = subject,
            timestamp = timestampMs,
            messageType = box,
            read = cursor.getInt(readIndex) == 1,
            status = statusSummary.statusCode,
            deliveryState = statusSummary.deliveryState,
            carrierAccepted = statusSummary.carrierAccepted,
            failureReason = statusSummary.failureReason,
            hasMedia = bodyAndAttachments.second.length() > 0,
            attachments = bodyAndAttachments.second,
            rawEventType = rawMmsEventType(box, mType, statusSummary),
          ),
        )
      }
    }

    return messages
  }

  private fun loadUnreadCounts(): Map<String, Int> {
    val counts = mutableMapOf<String, Int>()

    context.contentResolver.query(
      Telephony.Sms.Inbox.CONTENT_URI,
      arrayOf(Telephony.Sms.THREAD_ID),
      "${Telephony.Sms.READ} = 0",
      null,
      null,
    )?.use { cursor ->
      val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
      while (cursor.moveToNext()) {
        val threadId = cursor.getLong(threadIdIndex).toString()
        counts[threadId] = (counts[threadId] ?: 0) + 1
      }
    }

    context.contentResolver.query(
      Telephony.Mms.Inbox.CONTENT_URI,
      arrayOf(Telephony.Mms.THREAD_ID),
      "${Telephony.Mms.READ} = 0",
      null,
      null,
    )?.use { cursor ->
      val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
      while (cursor.moveToNext()) {
        val threadId = cursor.getLong(threadIdIndex).toString()
        counts[threadId] = (counts[threadId] ?: 0) + 1
      }
    }

    return counts
  }

  private fun loadMmsParticipants(messageId: Long): List<String> {
    val participants = linkedSetOf<String>()
    val uri = Uri.parse("content://mms/$messageId/addr")

    context.contentResolver.query(
      uri,
      arrayOf("address", "type"),
      null,
      null,
      null,
    )?.use { cursor ->
      val addressIndex = cursor.getColumnIndex("address")
      while (cursor.moveToNext()) {
        val address = cursor.getString(addressIndex).orEmpty()
        if (address.isBlank() || address == "insert-address-token") {
          continue
        }
        participants.add(address)
      }
    }

    return participants.toList()
  }

  private fun loadMmsBodyAndAttachments(messageId: Long): Pair<String, JSONArray> {
    val attachments = JSONArray()
    val textParts = mutableListOf<String>()

    context.contentResolver.query(
      Uri.parse("content://mms/part"),
      arrayOf("_id", "ct", "name", "cl", "text", "_data"),
      "mid = ?",
      arrayOf(messageId.toString()),
      null,
    )?.use { cursor ->
      val idIndex = cursor.getColumnIndex("_id")
      val contentTypeIndex = cursor.getColumnIndex("ct")
      val nameIndex = cursor.getColumnIndex("name")
      val locationIndex = cursor.getColumnIndex("cl")
      val textIndex = cursor.getColumnIndex("text")
      val dataIndex = cursor.getColumnIndex("_data")

      while (cursor.moveToNext()) {
        val partId = cursor.getLong(idIndex)
        val contentType = cursor.getString(contentTypeIndex).orEmpty()
        if (contentType == "application/smil") {
          continue
        }

        val text = if (textIndex >= 0) cursor.getString(textIndex).orEmpty() else ""
        if (contentType.startsWith("text/")) {
          val value =
            if (text.isNotBlank()) {
              text
            } else if (dataIndex >= 0 && !cursor.isNull(dataIndex)) {
              readBytes(Uri.parse("content://mms/part/$partId")).toString(Charsets.UTF_8)
            } else {
              ""
            }
          if (value.isNotBlank()) {
            textParts.add(value)
          }
          continue
        }

        val bytes = readBytes(Uri.parse("content://mms/part/$partId"))
        val fileName =
          cursor.getString(nameIndex).orEmpty().ifBlank {
            cursor.getString(locationIndex).orEmpty().ifBlank { "attachment-$partId" }
          }

        attachments.put(
          MmsSupport.encodeAttachmentPayload(
            id = "mms-part-$partId",
            fileName = fileName,
            mimeType = contentType,
            bytes = bytes,
            includeFullBase64 = true,
          ),
        )
      }
    }

    return textParts.joinToString("\n").trim() to attachments
  }

  private fun readBytes(uri: Uri): ByteArray =
    context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() } ?: ByteArray(0)

  private fun decodeSubject(subject: String?, charset: Int?): String? {
    if (subject.isNullOrBlank()) {
      return null
    }
    return subject
  }

  private fun messageToJson(message: GatewayMessage): JSONObject =
    JSONObject()
      .put("id", message.id)
      .put("kind", message.kind)
      .put("threadId", message.threadId)
      .put("address", message.address)
      .put("participants", JSONArray(message.participants))
      .put("displayName", message.displayName)
      .put("initials", message.initials)
      .put("body", message.body)
      .put("timestamp", message.timestamp)
      .put("messageType", message.messageType)
      .put("read", message.read)
      .put("status", message.status ?: JSONObject.NULL)
      .put("deliveryState", message.deliveryState ?: JSONObject.NULL)
      .put("carrierAccepted", message.carrierAccepted ?: JSONObject.NULL)
      .put("failureReason", message.failureReason ?: JSONObject.NULL)
      .put("subject", message.subject ?: JSONObject.NULL)
      .put("hasMedia", message.hasMedia)
      .put("attachments", message.attachments)

  private fun eventPayload(message: GatewayMessage): JSONObject =
    messageToJson(message)
      .put("subscriptionId", JSONObject.NULL)

  private fun conversationSnippet(message: GatewayMessage): String {
    if (message.deliveryState == "rejected") {
      return message.failureReason ?: "Carrier rejected the MMS"
    }
    if (message.deliveryState == "failed") {
      return message.failureReason?.let { "Failed: $it" } ?: "Failed to send"
    }
    if (message.body.isNotBlank()) {
      return message.body
    }
    if (message.attachments.length() > 0) {
      val attachment = message.attachments.optJSONObject(0)
      val label = attachment?.optString("fileName").orEmpty()
      return if (label.isNotBlank()) "Attachment: $label" else "Media message"
    }
    return message.subject.orEmpty()
  }

  private fun historyEventType(message: GatewayMessage): String =
    if (message.kind == "sms") {
      when (message.messageType) {
        Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX -> "sms.received"
        Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT -> "sms.outbound.sent"
        Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED -> "sms.outbound.failed"
        Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX -> "sms.outbound.accepted"
        else -> "sms.history"
      }
    } else {
      when (message.messageType) {
        Telephony.Mms.MESSAGE_BOX_INBOX -> "mms.received"
        Telephony.Mms.MESSAGE_BOX_SENT -> "mms.outbound.sent"
        Telephony.Mms.MESSAGE_BOX_OUTBOX -> "mms.outbound.accepted"
        Telephony.Mms.MESSAGE_BOX_FAILED -> "mms.outbound.failed"
        else -> "mms.history"
      }
    }

  private fun rawMmsEventType(box: Int, mType: Int?, statusSummary: MmsStatusSummary): String? {
    if (statusSummary.deliveryState == "rejected" || statusSummary.deliveryState == "failed") {
      return if (box == Telephony.Mms.MESSAGE_BOX_INBOX) null else "mms.outbound.failed"
    }

    return when (mType) {
      134, 136 -> "mms.outbound.delivered"
      130 -> "mms.notification"
      132 -> if (box == Telephony.Mms.MESSAGE_BOX_INBOX) "mms.received" else null
      else ->
        when (box) {
          Telephony.Mms.MESSAGE_BOX_INBOX -> "mms.received"
          Telephony.Mms.MESSAGE_BOX_SENT -> "mms.outbound.sent"
          Telephony.Mms.MESSAGE_BOX_OUTBOX -> "mms.outbound.accepted"
          Telephony.Mms.MESSAGE_BOX_FAILED -> "mms.outbound.failed"
          else -> null
        }
    }
  }

  private fun mmsTimestamp(cursor: android.database.Cursor, dateIndex: Int, dateSentIndex: Int): Long {
    val sentTimestamp =
      if (dateSentIndex >= 0 && !cursor.isNull(dateSentIndex)) cursor.getLong(dateSentIndex) else 0L
    val baseTimestamp =
      if (sentTimestamp > 0) sentTimestamp else cursor.getLong(dateIndex)
    return baseTimestamp * 1000L
  }

  private fun nullableInt(cursor: android.database.Cursor, index: Int): Int? =
    if (index < 0 || cursor.isNull(index)) null else cursor.getInt(index)

  private fun nullableString(cursor: android.database.Cursor, index: Int): String? =
    if (index < 0 || cursor.isNull(index)) null else cursor.getString(index)

  private fun smsDeliveryState(messageType: Int, status: Int?): String? =
    when (messageType) {
      Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED -> "failed"
      Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX -> "pending"
      Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT ->
        if (status != null && status >= 0) "sent" else null
      else -> null
    }

  private fun smsFailureReason(messageType: Int, status: Int?): String? =
    when {
      messageType == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED ->
        "SMS send failed${status?.let { " with status $it" } ?: ""}."
      else -> null
    }

  private fun attachmentsContainMimeType(attachments: JSONArray, mimeType: String): Boolean {
    for (index in 0 until attachments.length()) {
      val attachment = attachments.optJSONObject(index) ?: continue
      if (attachment.optString("mimeType").equals(mimeType, ignoreCase = true)) {
        return true
      }
    }
    return false
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

    val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon().appendPath(address).build()

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
