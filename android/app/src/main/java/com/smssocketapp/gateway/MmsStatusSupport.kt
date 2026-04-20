package com.smssocketapp.gateway

import android.app.Activity
import android.provider.Telephony
import android.telephony.SmsManager

data class MmsStatusSummary(
  val deliveryState: String?,
  val carrierAccepted: Boolean?,
  val failureReason: String?,
  val statusCode: Int?,
)

object MmsStatusSupport {
  private const val MMS_STATUS_OK = 0x80
  private const val MMS_STATUS_TRANSIENT_ERROR_START = 0xC0
  private const val MMS_STATUS_PERMANENT_ERROR_START = 0xE0
  private const val STALE_PENDING_MS = 15 * 60 * 1000L

  fun fromSendResultCode(resultCode: Int): MmsStatusSummary =
    when (resultCode) {
      Activity.RESULT_OK -> MmsStatusSummary("sent", true, null, null)
      SmsManager.MMS_ERROR_INVALID_APN ->
        MmsStatusSummary(
          "failed",
          false,
          "The carrier APN settings for MMS are invalid on this device.",
          resultCode,
        )
      SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS ->
        MmsStatusSummary(
          "failed",
          false,
          "The phone could not connect to the carrier MMS service.",
          resultCode,
        )
      SmsManager.MMS_ERROR_HTTP_FAILURE ->
        MmsStatusSummary(
          "failed",
          false,
          "The carrier MMS server rejected the request or returned an HTTP error.",
          resultCode,
        )
      SmsManager.MMS_ERROR_IO_ERROR ->
        MmsStatusSummary(
          "failed",
          false,
          "The phone hit an I/O error while preparing or sending the MMS.",
          resultCode,
        )
      SmsManager.MMS_ERROR_RETRY ->
        MmsStatusSummary(
          "failed",
          false,
          "The carrier asked Android to retry this MMS, but it still did not send.",
          resultCode,
        )
      SmsManager.MMS_ERROR_CONFIGURATION_ERROR ->
        MmsStatusSummary(
          "failed",
          false,
          "The device MMS configuration is incomplete or invalid for this carrier.",
          resultCode,
        )
      SmsManager.MMS_ERROR_NO_DATA_NETWORK ->
        MmsStatusSummary(
          "failed",
          false,
          "MMS requires a data connection, and none was available.",
          resultCode,
        )
      SmsManager.MMS_ERROR_INVALID_SUBSCRIPTION_ID ->
        MmsStatusSummary(
          "failed",
          false,
          "The selected SIM or subscription could not be used for MMS.",
          resultCode,
        )
      SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION ->
        MmsStatusSummary(
          "failed",
          false,
          "The selected SIM is inactive and the carrier would not accept the MMS.",
          resultCode,
        )
      SmsManager.MMS_ERROR_DATA_DISABLED ->
        MmsStatusSummary(
          "failed",
          false,
          "Mobile data is disabled, so the carrier MMS service could not be reached.",
          resultCode,
        )
      SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER ->
        MmsStatusSummary(
          "rejected",
          false,
          "This carrier has MMS disabled for the current line or plan.",
          resultCode,
        )
      Activity.RESULT_CANCELED ->
        MmsStatusSummary(
          "failed",
          false,
          "Android canceled the MMS before the carrier accepted it.",
          resultCode,
        )
      else ->
        MmsStatusSummary(
          "failed",
          false,
          "Android reported MMS send error code $resultCode.",
          resultCode,
        )
    }

  fun fromProvider(
    messageBox: Int,
    messageType: Int?,
    responseStatus: Int?,
    responseText: String?,
    status: Int?,
    retrieveStatus: Int?,
    retrieveText: String?,
    timestampMs: Long,
    nowMs: Long,
    hasPdfAttachment: Boolean,
  ): MmsStatusSummary {
    val normalizedResponseText = responseText?.trim().orEmpty()
    val normalizedRetrieveText = retrieveText?.trim().orEmpty()
    val responseFailure = describeResponseFailure(responseStatus)
    val retrieveFailure = describeRetrieveFailure(retrieveStatus)
    val failureReasonCandidate =
      normalizedResponseText.ifBlank {
        normalizedRetrieveText.ifBlank {
          responseFailure ?: retrieveFailure ?: defaultFailureReason(messageBox) ?: ""
        }
      }
    val failureReason = failureReasonCandidate.takeIf { it.isNotBlank() }
    val statusCode = responseStatus ?: retrieveStatus ?: status

    if (messageType == 134 || messageType == 136) {
      return MmsStatusSummary("delivered", true, null, statusCode)
    }

    if (messageBox == Telephony.Mms.MESSAGE_BOX_INBOX) {
      return MmsStatusSummary("received", null, retrieveFailure, statusCode)
    }

    if (
      messageBox == Telephony.Mms.MESSAGE_BOX_FAILED ||
        responseFailure != null ||
        retrieveFailure != null
    ) {
      val rejected = responseFailure != null || normalizedResponseText.isNotBlank()
      return MmsStatusSummary(
        deliveryState = if (rejected) "rejected" else "failed",
        carrierAccepted = if (rejected) false else null,
        failureReason = failureReason,
        statusCode = statusCode,
      )
    }

    if (messageBox == Telephony.Mms.MESSAGE_BOX_OUTBOX && nowMs - timestampMs >= STALE_PENDING_MS) {
      return MmsStatusSummary(
        deliveryState = "failed",
        carrierAccepted = false,
        failureReason =
          if (hasPdfAttachment) {
            "This MMS has been stuck in outbox for over 15 minutes. The carrier or device may not support PDF attachments over MMS."
          } else {
            "This MMS has been stuck in outbox for over 15 minutes without carrier confirmation."
          },
        statusCode = statusCode,
      )
    }

    return when (messageBox) {
      Telephony.Mms.MESSAGE_BOX_OUTBOX ->
        MmsStatusSummary("pending", null, null, statusCode)
      Telephony.Mms.MESSAGE_BOX_SENT ->
        MmsStatusSummary("sent", true, null, statusCode)
      else ->
        MmsStatusSummary(null, null, null, statusCode)
    }
  }

  private fun describeResponseFailure(responseStatus: Int?): String? =
    when (responseStatus) {
      null, MMS_STATUS_OK -> null
      0xC0 -> "The carrier reported a transient MMS failure."
      0xC1 -> "The carrier MMS service could not complete the request."
      0xC2 -> "The carrier MMS service is temporarily unavailable."
      0xC3 -> "The carrier network had a problem while accepting the MMS."
      0xC4 -> "The carrier reported only a partial MMS success."
      0xE0 -> "The carrier rejected the MMS."
      0xE1 -> "The carrier denied this MMS request."
      0xE2 -> "The carrier rejected the MMS because the message format was invalid."
      0xE3 -> "The carrier rejected the destination address for this MMS."
      0xE4 -> "The carrier could not route this MMS message."
      0xE5 -> "The carrier rejected the attachment or content in this MMS."
      0xE6, 0xE7, 0xE8, 0xE9 ->
        "The carrier rejected the MMS because billing or charging rules were not met."
      0xEA -> "The carrier does not support the requested address privacy settings."
      0xEB -> "The carrier rejected the MMS because the account balance is insufficient."
      else ->
        if (responseStatus >= MMS_STATUS_PERMANENT_ERROR_START) {
          "The carrier rejected the MMS with response status 0x${responseStatus.toString(16)}."
        } else if (responseStatus >= MMS_STATUS_TRANSIENT_ERROR_START) {
          "The carrier reported a transient MMS error 0x${responseStatus.toString(16)}."
        } else {
          null
        }
    }

  private fun describeRetrieveFailure(retrieveStatus: Int?): String? =
    when (retrieveStatus) {
      null, MMS_STATUS_OK -> null
      0xC0 -> "The MMS could not be retrieved because of a transient carrier failure."
      0xC1 -> "The carrier could not find the MMS content anymore."
      0xC2 -> "A carrier network problem prevented the MMS from being retrieved."
      0xE0 -> "The carrier permanently failed to retrieve the MMS."
      0xE1 -> "The carrier denied access to the MMS content."
      0xE2 -> "The carrier could not find the requested MMS content."
      0xE3 -> "The carrier reported that the MMS content is unsupported."
      else ->
        if (retrieveStatus >= MMS_STATUS_PERMANENT_ERROR_START) {
          "The carrier rejected MMS retrieval with status 0x${retrieveStatus.toString(16)}."
        } else if (retrieveStatus >= MMS_STATUS_TRANSIENT_ERROR_START) {
          "The carrier reported a temporary MMS retrieval error 0x${retrieveStatus.toString(16)}."
        } else {
          null
        }
    }

  private fun defaultFailureReason(messageBox: Int): String? =
    when (messageBox) {
      Telephony.Mms.MESSAGE_BOX_FAILED -> "The MMS could not be sent."
      else -> null
    }
}
