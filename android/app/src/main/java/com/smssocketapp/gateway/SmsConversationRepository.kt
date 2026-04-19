package com.smssocketapp.gateway

import android.content.Context
import org.json.JSONArray

class SmsConversationRepository(private val context: Context) {
  private val repository = GatewayMessageRepository(context)

  fun listConversations(limit: Int = 100): JSONArray = repository.listConversations(limit)

  fun getMessages(
    threadId: String? = null,
    address: String? = null,
    limit: Int = 200,
  ): JSONArray = repository.getMessages(threadId, address, limit)

  fun markConversationRead(threadId: String? = null, address: String? = null): Boolean =
    repository.markConversationRead(threadId, address)

  fun markConversationUnread(threadId: String? = null, address: String? = null): Boolean =
    repository.markConversationUnread(threadId, address)

  fun deleteConversation(threadId: String? = null, address: String? = null): Boolean =
    repository.deleteConversation(threadId, address)
}
