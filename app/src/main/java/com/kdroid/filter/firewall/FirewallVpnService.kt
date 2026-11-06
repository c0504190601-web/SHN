package com.kdroid.filter.firewall

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.kdroid.filter.apps.AppPolicyStore
import com.kdroid.filter.apps.AppRepository
import com.kdroid.filter.log.EventLog
import com.kdroid.filter.security.Prefs
import java.io.FileInputStream

/**
 * חומת אש לפי אפליקציה, ללא Root, מבוססת VPNService.
 *
 * עיקרון: מנהרה מקומית שאינה מעבירה תעבורה (drop). האפליקציות החסומות
 * מנותבות לתוך המנהרה — ומכיוון שאין העברה, אין להן אינטרנט.
 * האפליקציות המורשות וגם אפליקציית הניהול עצמה מוחרגות מהמנהרה (addDisallowedApplication)
 * וממשיכות לעבוד ישירות מול הרשת האמיתית.
 *
 * מצב "חסימת כל המכשיר": אף אפליקציה אינה מוחרגת (מלבד הניהול) ⇒ הכול נחסם.
 *
 * זהו מנגנון חסימה אמיתי. סינון DNS/דומיינים (שדורש העברת תעבורה) יתווסף בשלב 3ב.
 */
class FirewallVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null

    companion object {
        const val ACTION_START = "com.kdroid.filter.FW_START"
        const val ACTION_STOP = "com.kdroid.filter.FW_STOP"
        @Volatile var running: Boolean = false
            private set
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { teardown(); stopSelf(); return START_NOT_STICKY }
            else -> establish()
        }
        return START_STICKY
    }

    private fun establish() {
        teardown()
        val prefs = Prefs(this)
        val store = AppPolicyStore(this)
        val builder = Builder()
            .setSession("K-Droid Filter")
            .setMtu(1500)
            .addAddress("10.111.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addAddress("fd00:111::2", 128)
            .addRoute("::", 0)

        // תמיד להחריג את אפליקציית הניהול כדי לשמור על סנכרון לשרת.
        safeDisallow(builder, packageName)

        if (!prefs.blockWholeDevice) {
            // מצב רגיל: מחריגים (=מאפשרים אינטרנט) רק את המורשות. השאר נחסם.
            val allowed = store.allowedPackages()
            val installed = AppRepository(this).allPackageNames()
            allowed.filter { it != packageName && installed.contains(it) }
                .forEach { safeDisallow(builder, it) }
        }
        // אם blockWholeDevice=true — איש מלבד הניהול לא מוחרג ⇒ חסימה מלאה.

        try {
            val fd = builder.establish()
            if (fd == null) {
                EventLog.error(this, "Firewall", "establish() החזיר null — ייתכן שחסרה הסכמת VPN")
                running = false
                stopSelf()
                return
            }
            tun = fd
            running = true
            prefs.firewallEnabled = true
            EventLog.info(this, "Firewall", "חומת אש פעילה (חסימת כל המכשיר=${prefs.blockWholeDevice})")
            startReader(fd)
        } catch (e: Exception) {
            EventLog.error(this, "Firewall", "כשל הפעלת מנהרה: ${e.message}")
            running = false
            stopSelf()
        }
    }

    /** קורא ומשליך מנות מהמנהרה כדי שה-OS לא יחסום את ה-FD; זהו ה-drop בפועל. */
    private fun startReader(fd: ParcelFileDescriptor) {
        reader = Thread {
            val buf = ByteArray(32767)
            try {
                FileInputStream(fd.fileDescriptor).use { input ->
                    while (!Thread.currentThread().isInterrupted) {
                        val n = input.read(buf)
                        if (n < 0) break
                        // drop: לא מעבירים את המנה לשום מקום
                    }
                }
            } catch (_: Exception) {
                // סגירה/ניתוק — יציאה שקטה
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun safeDisallow(builder: Builder, pkg: String) {
        try { builder.addDisallowedApplication(pkg) } catch (_: Exception) { /* חבילה לא קיימת */ }
    }

    private fun teardown() {
        running = false
        reader?.interrupt(); reader = null
        try { tun?.close() } catch (_: Exception) {}
        tun = null
    }

    override fun onRevoke() {
        EventLog.warn(this, "Firewall", "ההרשאה בוטלה (VPN אחר?) — חומת האש נעצרה")
        teardown(); stopSelf()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }
}
