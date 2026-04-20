package com.smssocketapp.gateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayStatusSlicesTest {
  @Test
  fun `role state keeps sms and dialer roles independent`() {
    val smsOnly = GatewayStatusSlices.roleState(smsRoleGranted = true, dialerRoleGranted = false)
    val dialerOnly = GatewayStatusSlices.roleState(smsRoleGranted = false, dialerRoleGranted = true)

    assertTrue(smsOnly.getBoolean("smsRoleGranted"))
    assertFalse(smsOnly.getBoolean("dialerRoleGranted"))
    assertFalse(dialerOnly.getBoolean("smsRoleGranted"))
    assertTrue(dialerOnly.getBoolean("dialerRoleGranted"))
  }
}
