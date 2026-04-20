package com.smssocketapp.gateway

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GatewayDtmfTest {
  @Test
  fun `play sequence forwards multi digit tones in order`() {
    val events = mutableListOf<String>()
    val sleepCalls = mutableListOf<Long>()
    val player =
      object : GatewayDtmfTonePlayer {
        override fun playTone(digit: Char) {
          events += "play:$digit"
        }

        override fun stopTone() {
          events += "stop"
        }
      }

    GatewayDtmf.playSequence("12#", player) { durationMs ->
      sleepCalls += durationMs
    }

    assertEquals(
      listOf("play:1", "stop", "play:2", "stop", "play:#", "stop"),
      events,
    )
    assertEquals(
      listOf(
        GatewayDtmf.TONE_DURATION_MS,
        GatewayDtmf.INTER_DIGIT_PAUSE_MS,
        GatewayDtmf.TONE_DURATION_MS,
        GatewayDtmf.INTER_DIGIT_PAUSE_MS,
        GatewayDtmf.TONE_DURATION_MS,
      ),
      sleepCalls,
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun `normalize digits rejects unsupported characters`() {
    GatewayDtmf.normalizeDigits("12A")
  }

  @Test
  fun `dispatch dtmf reports call lookup failure`() {
    try {
      GatewayDialerManager.dispatchDtmf(
        callId = "missing-call",
        digits = "12",
        playerResolver = { requestedCallId ->
          throw IllegalArgumentException("Unknown callId: $requestedCallId")
        },
      )
    } catch (error: IllegalArgumentException) {
      assertEquals("Unknown callId: missing-call", error.message)
      return
    }

    throw AssertionError("Expected call lookup failure")
  }

  @Test
  fun `dispatch dtmf returns normalized success payload`() {
    val player =
      object : GatewayDtmfTonePlayer {
        override fun playTone(digit: Char) = Unit

        override fun stopTone() = Unit
      }
    var dispatchedDigits: String? = null
    val response =
      GatewayDialerManager.dispatchDtmf(
        callId = " call-123 ",
        digits = " 90* ",
        playerResolver = { player },
        sequenceSender = { normalizedDigits, _ ->
          dispatchedDigits = normalizedDigits
        },
      )

    assertEquals("90*", dispatchedDigits)
    assertEquals(
      JSONObject()
        .put("sent", true)
        .put("callId", "call-123")
        .put("digits", "90*")
        .toString(),
      response.toString(),
    )
  }
}
