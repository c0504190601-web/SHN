package com.kosherride.filter.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.kosherride.filter.security.Prefs

/**
 * סנכרון תקופתי עם השרת דרך WorkManager.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val client = SyncClient(applicationContext)
        // עדיין לא רשום: נרשם אוטומטית — רק כשהאפליקציה כבר Device Owner
        if (!prefs.isEnrolled) {
            val dpm = applicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            if (!dpm.isDeviceOwnerApp(applicationContext.packageName)) return Result.success()
            // נעילה: שתי משימות שרצות יחד (מיידית + תקופתית) לא יקבלו שני מספרים
            val ok = synchronized(ENROLL_LOCK) {
                prefs.isEnrolled || client.enroll(prefs.serverUrl ?: Prefs.DEFAULT_SERVER_URL).ok
            }
            if (!ok) return Result.retry()
        }
        return if (client.sync()) Result.success() else Result.retry()
    }

    companion object {
        const val NAME = "kosherride_sync"
        private val ENROLL_LOCK = Any()
    }
}
