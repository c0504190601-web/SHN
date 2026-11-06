package com.kdroid.filter.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kdroid.filter.firewall.FirewallVpnService
import com.kdroid.filter.kiosk.KioskController
import com.kdroid.filter.log.EventLog
import com.kdroid.filter.security.Prefs

/**
 * לאחר אתחול: טעינת המדיניות האחרונה והפעלת הרכיבים הנדרשים.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        EventLog.info(context, "Boot", "התקבל $a — טעינת מדיניות")
        val prefs = Prefs(context)

        // חומת אש: הפעלה מחדש אם הייתה פעילה (ב-Device Owner המערכת מפעילה Always-on לבד).
        if (prefs.firewallEnabled) {
            runCatching {
                context.startService(
                    Intent(context, FirewallVpnService::class.java).setAction(FirewallVpnService.ACTION_START)
                )
            }
        }

        // קיוסק: רענון רשימת האפליקציות המותרות. אם האפליקציה היא HOME, המערכת תפתח אותה לבד.
        val kiosk = KioskController(context)
        if (prefs.kioskEnabled) {
            kiosk.refreshLockTaskPackages()
            runCatching { kiosk.openKiosk() }
        }
    }
}
