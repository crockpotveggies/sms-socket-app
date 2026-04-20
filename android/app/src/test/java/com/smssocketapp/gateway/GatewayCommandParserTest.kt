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
    val sendDtmf =
      GatewayCommandParser.validate(
        "sendDtmf",
        JSONObject().put("callId", "call-123").put("digits", " 12# "),
      )
    val setMuted =
      GatewayCommandParser.validate(
        "setMuted",
        JSONObject().put("callId", "call-123").put("muted", true),
      )

    assertTrue(placeCall.ok)
    assertEquals("+15551234567", placeCall.payload.getString("number"))
    assertTrue(answerCall.ok)
    assertTrue(sendDtmf.ok)
    assertEquals("12#", sendDtmf.payload.getString("digits"))
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
    val invalidDigits =
      GatewayCommandParser.validate(
        "sendDtmf",
        JSONObject().put("callId", "call-123").put("digits", "12A"),
      )

    assertFalse(missingNumber.ok)
    assertEquals("number is required", missingNumber.error)
    assertFalse(missingCallId.ok)
    assertEquals("callId is required", missingCallId.error)
    assertFalse(invalidMuted.ok)
    assertEquals("callId and muted are required", invalidMuted.error)
    assertFalse(invalidDigits.ok)
    assertEquals("digits may contain only 0-9, *, and #", invalidDigits.error)
  }
}
