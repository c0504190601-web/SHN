package com.kdroid.filter.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.kdroid.filter.security.Prefs

/**
 * סנכרון תקופתי עם השרת דרך WorkManager.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        if (!Prefs(applicationContext).isEnrolled) return Result.success()
        return if (SyncClient(applicationContext).sync()) Result.success() else Result.retry()
    }

    companion object {
        const val NAME = "kdroid_sync"
    }
}
