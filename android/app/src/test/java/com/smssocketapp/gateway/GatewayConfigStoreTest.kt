package com.smssocketapp.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayConfigStoreTest {
  @Test
  fun previewUsesStoredSuffix() {
    val config = GatewayConfig(enabled = true, apiKeyPreview = "****ABCD")
    assertEquals("****ABCD", config.apiKeyPreview)
  }

  @Test
  fun configDefaultsUseWildcardHost() {
    val config = GatewayConfig()
    assertTrue(!config.enabled)
    assertEquals("0.0.0.0", config.host)
    assertEquals(8787, config.port)
  }
}
