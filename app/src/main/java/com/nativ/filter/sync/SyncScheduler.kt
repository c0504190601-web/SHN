package com.nativ.filter.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** תזמון רישום וסנכרון מול השרת. */
object SyncScheduler {
    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** רישום (אם צריך) וסנכרון מיידי, ואחר כך סנכרון כל 15 דקות. */
    fun now(ctx: Context) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        wm.enqueueUniqueWork(
            SyncWorker.NAME + "_now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(net).build()
        )
        wm.enqueueUniquePeriodicWork(
            SyncWorker.NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(net).build()
        )
    }
}
