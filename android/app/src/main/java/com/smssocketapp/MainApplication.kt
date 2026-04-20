package com.smssocketapp

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.smssocketapp.gateway.GatewayDialerManager
import com.smssocketapp.gateway.GatewayRuntime
import com.smssocketapp.gateway.SmsGatewayPackage

class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          add(SmsGatewayPackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    GatewayRuntime.initialize(this)
    GatewayDialerManager.initialize(this)
    loadReactNative(this)
  }
}
