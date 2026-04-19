package com.smssocketapp.gateway

import android.content.Context
import org.json.JSONArray

class SmsHistoryRepository(private val context: Context) {
  fun rehydrateSince(since: Long, limit: Int): JSONArray =
    GatewayMessageRepository(context).rehydrateSince(since, limit)
}
