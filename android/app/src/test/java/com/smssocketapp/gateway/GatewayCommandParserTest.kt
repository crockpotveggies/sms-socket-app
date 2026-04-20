package com.smssocketapp.gateway

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayCommandParserTest {
  @Test
  fun `accepts dialer commands with valid payloads`() {
    val placeCall = GatewayCommandParser.validate("placeCall", JSONObject().put("number", " +15551234567 "))
    val answerCall = GatewayCommandParser.validate("answerCall", JSONObject().put("callId", "call-123"))
    val setMuted =
      GatewayCommandParser.validate(
        "setMuted",
        JSONObject().put("callId", "call-123").put("muted", true),
      )

    assertTrue(placeCall.ok)
    assertEquals("+15551234567", placeCall.payload.getString("number"))
    assertTrue(answerCall.ok)
    assertTrue(setMuted.ok)
    assertTrue(setMuted.payload.getBoolean("muted"))
  }

  @Test
  fun `rejects invalid dialer payloads`() {
    val missingNumber = GatewayCommandParser.validate("placeCall", JSONObject())
    val missingCallId = GatewayCommandParser.validate("endCall", JSONObject())
    val invalidMuted =
      GatewayCommandParser.validate(
        "setMuted",
        JSONObject().put("callId", "call-123").put("muted", "yes"),
      )

    assertFalse(missingNumber.ok)
    assertEquals("number is required", missingNumber.error)
    assertFalse(missingCallId.ok)
    assertEquals("callId is required", missingCallId.error)
    assertFalse(invalidMuted.ok)
    assertEquals("callId and muted are required", invalidMuted.error)
  }
}
