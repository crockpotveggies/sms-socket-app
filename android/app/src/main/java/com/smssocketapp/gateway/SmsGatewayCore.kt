package com.smssocketapp.gateway

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import org.json.JSONObject
import java.util.UUID

object SmsGatewayCore {
  const val EXTRA_MESSAGE_ID = "messageId"
  const val EXTRA_DESTINATION = "destination"
  const val EXTRA_BODY = "body"
  const val EXTRA_SUBSCRIPTION_ID = "subscriptionId"

  fun handleIncomingSms(
    context: Context,
    address: String,
    body: String,
    timestamp: Long,
    subscriptionId: Int?,
  ) {
    val values =
      ContentValues().apply {
        put(Telephony.Sms.ADDRESS, address)
        put(Telephony.Sms.BODY, body)
        put(Telephony.Sms.DATE, timestamp)
        put(Telephony.Sms.READ, 0)
      }
    context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)

    GatewayRuntime.recordEvent(
      "sms.received",
      JSONObject()
        .put("address", address)
        .put("body", body)
        .put("subscriptionId", subscriptionId ?: JSONObject.NULL)
        .put("receivedAt", timestamp),
    )
  }

  fun enqueueOutboundSms(
    context: Context,
    destination: String,
    body: String,
    subscriptionId: Int?,
  ): JSONObject {
    val messageId = UUID.randomUUID().toString()
    val payload =
      JSONObject()
        .put("messageId", messageId)
        .put("destination", destination)
        .put("body", body)
        .put("subscriptionId", subscriptionId ?: JSONObject.NULL)

    PendingMessageStore(context).put(messageId, payload)

    val sentIntent =
      PendingIntent.getBroadcast(
        context,
        messageId.hashCode(),
        Intent(context, SmsSentReceiver::class.java).apply {
          putExtra(EXTRA_MESSAGE_ID, messageId)
          putExtra(EXTRA_DESTINATION, destination)
          putExtra(EXTRA_BODY, body)
          subscriptionId?.let { putExtra(EXTRA_SUBSCRIPTION_ID, it) }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val deliveredIntent =
      PendingIntent.getBroadcast(
        context,
        messageId.hashCode() + 1,
        Intent(context, SmsDeliveredReceiver::class.java).apply {
          putExtra(EXTRA_MESSAGE_ID, messageId)
          putExtra(EXTRA_DESTINATION, destination)
          putExtra(EXTRA_BODY, body)
          subscriptionId?.let { putExtra(EXTRA_SUBSCRIPTION_ID, it) }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val smsManager =
      if (subscriptionId != null && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
        SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
      } else {
        SmsManager.getDefault()
      }

    val parts = smsManager.divideMessage(body)
    if (parts.size > 1) {
      val sentIntents = ArrayList<PendingIntent?>()
      val deliveredIntents = ArrayList<PendingIntent?>()
      parts.forEachIndexed { index, _ ->
        sentIntents.add(if (index == 0) sentIntent else null)
        deliveredIntents.add(if (index == 0) deliveredIntent else null)
      }
      smsManager.sendMultipartTextMessage(
        destination,
        null,
        parts,
        sentIntents,
        deliveredIntents,
      )
    } else {
      smsManager.sendTextMessage(destination, null, body, sentIntent, deliveredIntent)
    }

    context.contentResolver.insert(
      Telephony.Sms.Sent.CONTENT_URI,
      ContentValues().apply {
        put(Telephony.Sms.ADDRESS, destination)
        put(Telephony.Sms.BODY, body)
        put(Telephony.Sms.DATE, System.currentTimeMillis())
        put(Telephony.Sms.READ, 1)
      },
    )

    GatewayRuntime.recordEvent("sms.outbound.accepted", payload)
    return payload
  }

  fun handleSendResult(context: Context, intent: Intent, resultCode: Int) {
    val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
    val pending = PendingMessageStore(context).get(messageId) ?: JSONObject()
    val destination = intent.getStringExtra(EXTRA_DESTINATION)
    val body = intent.getStringExtra(EXTRA_BODY)
    val subscriptionId =
      if (intent.hasExtra(EXTRA_SUBSCRIPTION_ID)) intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1) else null

    val payload =
      JSONObject(pending.toString())
        .put("destination", destination)
        .put("body", body)
        .put("subscriptionId", subscriptionId ?: JSONObject.NULL)

    if (resultCode == Activity.RESULT_OK) {
      GatewayRuntime.recordEvent("sms.outbound.sent", payload)
    } else {
      GatewayRuntime.recordEvent(
        "sms.outbound.failed",
        payload.put("errorCode", resultCode).put("stage", "sent"),
      )
      PendingMessageStore(context).remove(messageId)
    }
  }

  fun handleDeliveryResult(context: Context, intent: Intent, resultCode: Int) {
    val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
    val pending = PendingMessageStore(context).get(messageId) ?: JSONObject()

    val payload = JSONObject(pending.toString())

    if (resultCode == Activity.RESULT_OK) {
      GatewayRuntime.recordEvent("sms.outbound.delivered", payload)
    } else {
      GatewayRuntime.recordEvent(
        "sms.outbound.failed",
        payload.put("errorCode", resultCode).put("stage", "delivery"),
      )
    }

    PendingMessageStore(context).remove(messageId)
  }
}
