package com.smssocketapp.gateway

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SmsGatewayHealthWorker(
  appContext: Context,
  params: WorkerParameters,
) : Worker(appContext, params) {
  override fun doWork(): Result {
    val config = GatewayConfigStore(applicationContext).load()
    if (config.enabled && !GatewayRuntime.isRunning()) {
      GatewayForegroundService.ensureStarted(applicationContext)
    }
    return Result.success()
  }

  companion object {
    private const val UNIQUE_WORK_NAME = "sms_gateway_health"

    fun schedule(context: Context) {
      val request =
        PeriodicWorkRequestBuilder<SmsGatewayHealthWorker>(15, TimeUnit.MINUTES).build()
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        UNIQUE_WORK_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        request,
      )
    }

    fun cancel(context: Context) {
      WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
  }
}
