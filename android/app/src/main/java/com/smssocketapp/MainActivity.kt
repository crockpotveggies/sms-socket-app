package com.smssocketapp

import android.app.KeyguardManager
import android.os.Bundle
import androidx.core.content.getSystemService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    applyTelecomIntent(intent)
    super.onCreate(savedInstanceState)
  }

  override fun onNewIntent(intent: android.content.Intent?) {
    super.onNewIntent(intent)
    setIntent(intent)
    applyTelecomIntent(intent)
  }

  override fun getMainComponentName(): String = "SmsSocketApp"

  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

  private fun applyTelecomIntent(intent: android.content.Intent?) {
    if (intent?.getBooleanExtra("showWhenLocked", false) != true) {
      return
    }

    setShowWhenLocked(true)
    setTurnScreenOn(true)
    getSystemService<KeyguardManager>()?.requestDismissKeyguard(this, null)
  }
}
