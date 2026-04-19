package com.smssocketapp.gateway

import org.json.JSONArray
import org.json.JSONObject

object GatewayEventSanitizer {
  private val redactedAttachmentFields = setOf("base64", "previewBase64")
  private val attachmentContainerKeys = setOf("attachment", "attachments")

  fun sanitizePayload(payload: JSONObject): JSONObject = sanitizeObject(payload, null)

  fun sanitizeEventRecord(event: JSONObject): JSONObject {
    val sanitized = JSONObject(event.toString())
    val payload = sanitized.optJSONObject("payload") ?: return sanitized
    sanitized.put("payload", sanitizePayload(payload))
    return sanitized
  }

  private fun sanitizeValue(value: Any?, parentKey: String?): Any? =
    when (value) {
      is JSONObject -> sanitizeObject(value, parentKey)
      is JSONArray -> sanitizeArray(value, parentKey)
      else -> value
    }

  private fun sanitizeObject(source: JSONObject, parentKey: String?): JSONObject {
    val sanitized = JSONObject()
    val isAttachment = looksLikeAttachment(source, parentKey)
    var redactedContent = false
    val keys = source.keys()

    while (keys.hasNext()) {
      val key = keys.next()
      if (isAttachment && key in redactedAttachmentFields && !source.isNull(key)) {
        redactedContent = true
        continue
      }

      sanitized.put(key, sanitizeValue(source.opt(key), key))
    }

    if (isAttachment && redactedContent) {
      sanitized.put("contentRedacted", true)
    }

    return sanitized
  }

  private fun sanitizeArray(source: JSONArray, parentKey: String?): JSONArray {
    val sanitized = JSONArray()
    for (index in 0 until source.length()) {
      sanitized.put(sanitizeValue(source.opt(index), parentKey))
    }
    return sanitized
  }

  private fun looksLikeAttachment(candidate: JSONObject, parentKey: String?): Boolean =
    parentKey in attachmentContainerKeys ||
      candidate.has("mimeType") ||
      candidate.has("fileName") ||
      candidate.has("sizeBytes")
}
