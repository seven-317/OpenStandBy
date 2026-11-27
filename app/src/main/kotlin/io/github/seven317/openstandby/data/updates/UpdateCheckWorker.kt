package io.github.seven317.openstandby.data.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.seven317.openstandby.OpenStandByApplication
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val updates = (applicationContext as OpenStandByApplication).container.appUpdates
        return if (updates.check() && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        internal fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "check_for_app_updates", ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }
    }
}
