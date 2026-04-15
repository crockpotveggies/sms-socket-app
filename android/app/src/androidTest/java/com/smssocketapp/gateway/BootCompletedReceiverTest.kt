package com.smssocketapp.gateway

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootCompletedReceiverTest {
  @Test
  fun onReceiveDoesNotCrashWhenGatewayDisabled() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    GatewayConfigStore(context).setEnabled(false)

    BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
  }
}
