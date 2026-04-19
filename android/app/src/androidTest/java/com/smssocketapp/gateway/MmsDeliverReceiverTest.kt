package com.smssocketapp.gateway

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MmsDeliverReceiverTest {
  @Test
  fun onReceiveRecordsGatewayEventWithoutCrashing() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    GatewayRuntime.initialize(context)
    context.getSharedPreferences(GatewayConfigStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()

    MmsDeliverReceiver().onReceive(
      context,
      Intent("android.provider.Telephony.WAP_PUSH_DELIVER"),
    )

    var sawExpectedEvent = false
    val deadline = System.currentTimeMillis() + 4_000L
    while (System.currentTimeMillis() < deadline) {
      val recent = GatewayEventStore(context).getRecent(10)
      if (recent.length() > 0) {
        for (index in 0 until recent.length()) {
          val event = recent.getJSONObject(index)
          val type = event.optString("type")
          if (
            type == "gateway.error" ||
              type == "gateway.event" ||
              type == "mms.received" ||
              type == "mms.notification"
          ) {
            sawExpectedEvent = true
            break
          }
        }
      }
      if (sawExpectedEvent) {
        break
      }
      Thread.sleep(100)
    }

    assertTrue(sawExpectedEvent)
  }
}
