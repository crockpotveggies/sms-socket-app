package com.smssocketapp.gateway

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

data class GatewayConfig(
  val enabled: Boolean = false,
  val host: String = "0.0.0.0",
  val port: Int = 8787,
  val apiKeyHash: String? = null,
  val apiKeyPreview: String = "",
)

class GatewayConfigStore(private val context: Context) {
  private val preferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun load(): GatewayConfig =
    GatewayConfig(
      enabled = preferences.getBoolean(KEY_ENABLED, false),
      host = preferences.getString(KEY_HOST, "0.0.0.0") ?: "0.0.0.0",
      port = preferences.getInt(KEY_PORT, 8787),
      apiKeyHash = preferences.getString(KEY_API_KEY_HASH, null),
      apiKeyPreview = preferences.getString(KEY_API_KEY_PREVIEW, "") ?: "",
    )

  fun save(config: GatewayConfig) {
    preferences
      .edit()
      .putBoolean(KEY_ENABLED, config.enabled)
      .putString(KEY_HOST, config.host)
      .putInt(KEY_PORT, config.port)
      .putString(KEY_API_KEY_HASH, config.apiKeyHash)
      .putString(KEY_API_KEY_PREVIEW, config.apiKeyPreview)
      .apply()
  }

  fun setEnabled(enabled: Boolean) {
    preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
  }

  fun storeApiKey(apiKey: String): String {
    val preview = previewFor(apiKey)
    preferences
      .edit()
      .putString(KEY_API_KEY_HASH, sha256(apiKey))
      .putString(KEY_API_KEY_PREVIEW, preview)
      .apply()
    return preview
  }

  fun hasApiKey(): Boolean = !preferences.getString(KEY_API_KEY_HASH, null).isNullOrBlank()

  fun validateApiKey(apiKey: String?): Boolean {
    if (apiKey.isNullOrBlank()) {
      return false
    }

    return sha256(apiKey) == preferences.getString(KEY_API_KEY_HASH, null)
  }

  fun generateApiKey(): String {
    val random = ByteArray(32)
    SecureRandom().nextBytes(random)
    return Base64.encodeToString(random, Base64.NO_WRAP or Base64.URL_SAFE)
  }

  private fun previewFor(apiKey: String): String = "****${apiKey.takeLast(4)}"

  private fun sha256(raw: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
  }

  companion object {
    const val PREFS_NAME = "sms_gateway"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"
    private const val KEY_API_KEY_HASH = "apiKeyHash"
    private const val KEY_API_KEY_PREVIEW = "apiKeyPreview"
  }
}
