package com.smssocketapp.gateway

interface GatewayDtmfTonePlayer {
  fun playTone(digit: Char)

  fun stopTone()
}

object GatewayDtmf {
  const val TONE_DURATION_MS = 150L
  const val INTER_DIGIT_PAUSE_MS = 75L

  private val supportedDigits = "0123456789*#".toSet()

  fun normalizeDigits(rawDigits: String): String {
    val digits = rawDigits.trim()
    if (digits.isBlank()) {
      throw IllegalArgumentException("digits is required")
    }

    val invalidDigit = digits.firstOrNull { it !in supportedDigits }
    if (invalidDigit != null) {
      throw IllegalArgumentException("digits may contain only 0-9, *, and #")
    }

    return digits
  }

  fun playSequence(
    digits: String,
    player: GatewayDtmfTonePlayer,
    sleep: (Long) -> Unit = { durationMs -> Thread.sleep(durationMs) },
  ) {
    digits.forEachIndexed { index, digit ->
      player.playTone(digit)
      try {
        sleep(TONE_DURATION_MS)
      } finally {
        player.stopTone()
      }

      if (index < digits.lastIndex) {
        sleep(INTER_DIGIT_PAUSE_MS)
      }
    }
  }
}
