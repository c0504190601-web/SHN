package com.nativ.filter.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.nativ.filter.security.Prefs

/**
 * סנכרון תקופתי עם השרת דרך WorkManager.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val client = SyncClient(applicationContext)
        // עדיין לא רשום: נרשם אוטומטית לשרת ברירת המחדל
        if (!prefs.isEnrolled) {
            val r = client.enroll(prefs.serverUrl ?: Prefs.DEFAULT_SERVER_URL)
            if (!r.ok) return Result.retry()
        }
        return if (client.sync()) Result.success() else Result.retry()
    }

    companion object {
        const val NAME = "nativ_sync"
    }
}
