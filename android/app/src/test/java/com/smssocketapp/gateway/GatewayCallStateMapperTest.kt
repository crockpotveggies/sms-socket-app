package com.smssocketapp.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayCallStateMapperTest {
  @Test
  fun `stable id registry keeps ids stable per object`() {
    val registry = StableIdRegistry<Any>()
    val first = Any()
    val second = Any()

    val firstId = registry.idFor(first)

    assertEquals(firstId, registry.idFor(first))
    assertNotEquals(firstId, registry.idFor(second))
  }

  @Test
  fun `call mapper emits capability flags for ringing and active calls`() {
    val ringing =
      GatewayCallStateMapper.toSnapshot(
        callId = "call-1",
        descriptor =
          GatewayCallDescriptor(
            number = "+15551234567",
            displayName = "Taylor",
            direction = "incoming",
            state = "ringing",
            isMuted = false,
            route = "bluetooth",
            isConference = false,
          ),
      )
    val active =
      GatewayCallStateMapper.toSnapshot(
        callId = "call-2",
        descriptor =
          GatewayCallDescriptor(
            number = "+15557654321",
            displayName = "Morgan",
            direction = "outgoing",
            state = "active",
            isMuted = true,
            route = "speaker",
            isConference = true,
          ),
      )

    assertTrue(ringing.canAnswer)
    assertTrue(ringing.canReject)
    assertTrue(ringing.canDisconnect)
    assertFalse(active.canAnswer)
    assertFalse(active.canReject)
    assertTrue(active.canDisconnect)
    assertTrue(active.isMuted)
    assertTrue(active.isConference)
  }
}
