package com.smssocketapp.gateway

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayEventSanitizerTest {
  @Test
  fun `sanitize payload strips heavy attachment content but preserves metadata`() {
    val payload =
      JSONObject()
        .put("messageId", "mms-1")
        .put(
          "attachment",
          JSONObject()
            .put("id", "attachment-1")
            .put("fileName", "statement.pdf")
            .put("mimeType", "application/pdf")
            .put("sizeBytes", 321_000)
            .put("base64", "ZmFrZS1wZGY="),
        )
        .put(
          "attachments",
          JSONArray().put(
            JSONObject()
              .put("id", "image-1")
              .put("fileName", "preview.jpg")
              .put("mimeType", "image/jpeg")
              .put("sizeBytes", 98_000)
              .put("base64", "ZmFrZS1pbWFnZQ==")
              .put("previewBase64", "c21hbGwtcHJldmlldw=="),
          ),
        )

    val sanitized = GatewayEventSanitizer.sanitizePayload(payload)

    val directAttachment = sanitized.getJSONObject("attachment")
    assertEquals("statement.pdf", directAttachment.getString("fileName"))
    assertFalse(directAttachment.has("base64"))
    assertTrue(directAttachment.getBoolean("contentRedacted"))

    val arrayAttachment = sanitized.getJSONArray("attachments").getJSONObject(0)
    assertEquals("image/jpeg", arrayAttachment.getString("mimeType"))
    assertFalse(arrayAttachment.has("base64"))
    assertFalse(arrayAttachment.has("previewBase64"))
    assertTrue(arrayAttachment.getBoolean("contentRedacted"))
  }

  @Test
  fun `sanitize payload leaves non attachment base64 fields alone`() {
    val payload =
      JSONObject()
        .put("type", "gateway.debug")
        .put("base64", "keep-me")
        .put("nested", JSONObject().put("base64", "also-keep"))

    val sanitized = GatewayEventSanitizer.sanitizePayload(payload)

    assertEquals("keep-me", sanitized.getString("base64"))
    assertEquals("also-keep", sanitized.getJSONObject("nested").getString("base64"))
  }

  @Test
  fun `sanitize event record redacts nested payload attachments`() {
    val event =
      JSONObject()
        .put("id", "event-1")
        .put("type", "mms.outbound.accepted")
        .put("timestamp", 1234L)
        .put(
          "payload",
          JSONObject()
            .put("destination", "+15555550123")
            .put(
              "attachment",
              JSONObject()
                .put("fileName", "invoice.pdf")
                .put("mimeType", "application/pdf")
                .put("sizeBytes", 64000)
                .put("base64", "ZmFrZQ=="),
            ),
        )

    val sanitized = GatewayEventSanitizer.sanitizeEventRecord(event)
    val sanitizedAttachment = sanitized.getJSONObject("payload").getJSONObject("attachment")

    assertFalse(sanitizedAttachment.has("base64"))
    assertTrue(sanitizedAttachment.getBoolean("contentRedacted"))
  }

  @Test
  fun `describe mms send failure explains carrier handoff uncertainty`() {
    assertEquals(
      "Android canceled the MMS before the carrier accepted it.",
      SmsGatewayCore.describeMmsSendFailure(android.app.Activity.RESULT_CANCELED),
    )
    assertEquals(
      "Android reported MMS send error code 42.",
      SmsGatewayCore.describeMmsSendFailure(42),
    )
  }
}
