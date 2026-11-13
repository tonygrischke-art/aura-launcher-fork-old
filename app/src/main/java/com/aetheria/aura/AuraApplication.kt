package com.aetheria.aura

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import com.aetheria.aura.db.AuraDatabase
import com.aetheria.aura.model.ModelFileManager
import com.aetheria.aura.nim.DailyHealthCheckWorker
import com.aetheria.aura.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AuraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Pre-initialize Room database to ensure it's created and persisted
        CoroutineScope(Dispatchers.IO).launch {
            AuraDatabase.getInstance(this@AuraApplication)
        }
        ModelFileManager.status(this)
        ShizukuManager.init(this)
        scheduleDailyHealthCheck()
    }

    private fun scheduleDailyHealthCheck() {
        val workRequest = PeriodicWorkRequestBuilder<DailyHealthCheckWorker>(24, TimeUnit.HOURS)
            .setConstraints(androidx.work.Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "DailyModelHealthCheck",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
    }

    companion object {
        /** Manually trigger health check (for testing) */
        fun triggerHealthCheckNow(context: Context) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<DailyHealthCheckWorker>()
                .setConstraints(androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}