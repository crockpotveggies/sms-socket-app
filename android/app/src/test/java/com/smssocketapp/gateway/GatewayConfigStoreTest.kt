package com.smssocketapp.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

  // ---- isNetworkEquivalent --------------------------------------
  //
  // GatewayRuntime.startServer uses this to short-circuit the
  // SMS-broadcast restart cycle (see comment in
  // GatewayConfig.isNetworkEquivalent). A regression here would
  // either:
  //   * make startServer non-idempotent again → every inbound SMS
  //     would kick every connected WebSocket client (the bug we
  //     just fixed), OR
  //   * make startServer idempotent across REAL config changes
  //     → operator changes the port and the new port doesn't
  //     take effect.

  @Test
  fun networkEquivalent_when_host_port_apiKeyHash_match() {
    val a = GatewayConfig(
      enabled = true, host = "0.0.0.0", port = 8787,
      apiKeyHash = "deadbeef", apiKeyPreview = "****beef",
    )
    val b = a.copy()
    assertTrue(a.isNetworkEquivalent(b))
  }

  @Test
  fun networkEquivalent_ignores_enabled_flag() {
    // `enabled` is consumed by onStartCommand BEFORE startServer
    // is called; once we're inside startServer, enabled is
    // implicitly true. Treat it as not-meaningful.
    val a = GatewayConfig(enabled = true, port = 8787, apiKeyHash = "x")
    val b = a.copy(enabled = false)
    assertTrue(a.isNetworkEquivalent(b))
  }

  @Test
  fun networkEquivalent_ignores_preview_when_hash_matches() {
    // Preview is display-only; same hash means clients still
    // authenticate the same way.
    val a = GatewayConfig(port = 8787, apiKeyHash = "x", apiKeyPreview = "****abcd")
    val b = a.copy(apiKeyPreview = "****beef")
    assertTrue(a.isNetworkEquivalent(b))
  }

  @Test
  fun notNetworkEquivalent_when_port_changes() {
    val a = GatewayConfig(host = "0.0.0.0", port = 8787, apiKeyHash = "x")
    val b = a.copy(port = 9090)
    assertFalse(a.isNetworkEquivalent(b))
  }

  @Test
  fun notNetworkEquivalent_when_host_changes() {
    val a = GatewayConfig(host = "0.0.0.0", port = 8787, apiKeyHash = "x")
    val b = a.copy(host = "127.0.0.1")
    assertFalse(a.isNetworkEquivalent(b))
  }

  @Test
  fun notNetworkEquivalent_when_apiKeyHash_rotates() {
    // Rotated key → existing clients holding the old key need to
    // re-auth → server MUST cycle so the auth-validator picks up
    // the new hash.
    val a = GatewayConfig(port = 8787, apiKeyHash = "old-hash")
    val b = a.copy(apiKeyHash = "new-hash")
    assertFalse(a.isNetworkEquivalent(b))
  }

  @Test
  fun notNetworkEquivalent_when_apiKeyHash_set_for_first_time() {
    // Initial install: hash goes from null → some-value. That's
    // a real change; cycle.
    val a = GatewayConfig(port = 8787, apiKeyHash = null)
    val b = a.copy(apiKeyHash = "first-hash")
    assertFalse(a.isNetworkEquivalent(b))
    assertFalse(b.isNetworkEquivalent(a))
  }
}
