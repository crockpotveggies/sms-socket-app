package com.smssocketapp.gateway

import android.app.Activity
import android.provider.Telephony
import android.telephony.SmsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsStatusSupportTest {
  @Test
  fun `maps Android MMS send callback errors into user facing failure reasons`() {
    val noData = MmsStatusSupport.fromSendResultCode(SmsManager.MMS_ERROR_NO_DATA_NETWORK)
    assertEquals("failed", noData.deliveryState)
    assertEquals(false, noData.carrierAccepted)
    assertTrue(noData.failureReason?.contains("data connection") == true)

    val carrierDisabled =
      MmsStatusSupport.fromSendResultCode(SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER)
    assertEquals("rejected", carrierDisabled.deliveryState)
    assertEquals(false, carrierDisabled.carrierAccepted)
    assertTrue(carrierDisabled.failureReason?.contains("carrier") == true)

    val canceled = MmsStatusSupport.fromSendResultCode(Activity.RESULT_CANCELED)
    assertEquals("failed", canceled.deliveryState)
    assertTrue(canceled.failureReason?.contains("Android canceled") == true)
  }

  @Test
  fun `detects carrier rejection from provider response status`() {
    val summary =
      MmsStatusSupport.fromProvider(
        messageBox = Telephony.Mms.MESSAGE_BOX_FAILED,
        messageType = 128,
        responseStatus = 0xE5,
        responseText = null,
        status = null,
        retrieveStatus = null,
        retrieveText = null,
      )

    assertEquals("rejected", summary.deliveryState)
    assertEquals(false, summary.carrierAccepted)
    assertTrue(summary.failureReason?.contains("attachment or content") == true)
    assertEquals(0xE5, summary.statusCode)
  }

  @Test
  fun `detects pending and delivered provider states`() {
    val pending =
      MmsStatusSupport.fromProvider(
        messageBox = Telephony.Mms.MESSAGE_BOX_OUTBOX,
        messageType = 128,
        responseStatus = null,
        responseText = null,
        status = null,
        retrieveStatus = null,
        retrieveText = null,
      )
    assertEquals("pending", pending.deliveryState)
    assertNull(pending.failureReason)

    val delivered =
      MmsStatusSupport.fromProvider(
        messageBox = Telephony.Mms.MESSAGE_BOX_SENT,
        messageType = 134,
        responseStatus = null,
        responseText = null,
        status = 0x80,
        retrieveStatus = null,
        retrieveText = null,
      )
    assertEquals("delivered", delivered.deliveryState)
    assertEquals(true, delivered.carrierAccepted)
  }
}
