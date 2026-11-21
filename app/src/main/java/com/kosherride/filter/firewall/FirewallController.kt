package com.kosherride.filter.firewall

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.kosherride.filter.admin.AdminReceiver
import com.kosherride.filter.apps.AppPolicyStore
import com.kosherride.filter.apps.AppRepository
import com.kosherride.filter.log.EventLog
import com.kosherride.filter.security.Prefs

/**
 * הפעלה/כיבוי של חומת האש וניהול Always-on.
 * כ-Device Owner אפשר לכפות Always-on VPN עם Lockdown — המשתמש לא יוכל לכבות,
 * וגם אין צורך בדיאלוג הסכמה.
 */
class FirewallController(private val ctx: Context) {

    private val prefs = Prefs(ctx)
    private val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = AdminReceiver.component(ctx)

    fun isRunning(): Boolean = FirewallVpnService.running

    /** אם מוחזר Intent — יש להציג דיאלוג הסכמת VPN (רק כשאין Device Owner). */
    fun consentIntent(): Intent? = VpnService.prepare(ctx)

    fun enable() {
        // כ-Device Owner: Always-on + Lockdown, שמונע כיבוי ומבטל צורך בהסכמה.
        if (dpm.isDeviceOwnerApp(ctx.packageName)) {
            try {
                // בלי Lockdown: במצב Lockdown אנדרואיד חוסם כל תעבורה שלא עוברת במנהרה —
                // כולל אפליקציות מורשות (שיוצאות מהמנהרה) וכל מה שאינו DNS במצב סינון. זה ניתק את כל המכשיר.
                // כיבוי ה-VPN על ידי המשתמש נמנע בנפרד בהגבלת "נעילת הגדרות VPN".
                dpm.setAlwaysOnVpnPackage(admin, ctx.packageName, false)
                EventLog.info(ctx, "Firewall", "הוגדר Always-on VPN")
            } catch (e: Exception) {
                EventLog.warn(ctx, "Firewall", "setAlwaysOnVpnPackage נכשל: ${e.message}")
            }
        }
        startService(FirewallVpnService.ACTION_START)
        prefs.firewallEnabled = true
    }

    fun disable() {
        if (dpm.isDeviceOwnerApp(ctx.packageName)) {
            try { dpm.setAlwaysOnVpnPackage(admin, null, false) } catch (_: Exception) {}
        }
        startService(FirewallVpnService.ACTION_STOP)
        prefs.firewallEnabled = false
    }

    fun setBlockWholeDevice(block: Boolean) {
        prefs.blockWholeDevice = block
        if (isRunning()) startService(FirewallVpnService.ACTION_START) // בנייה מחדש של המנהרה
    }

    fun blockWholeDevice(): Boolean = prefs.blockWholeDevice

    fun filterMode(): Boolean = prefs.filterMode
    fun setFilterMode(filter: Boolean) {
        prefs.filterMode = filter
        if (isRunning()) startService(FirewallVpnService.ACTION_START) // בנייה מחדש במצב החדש
    }

    /** מסיר Lockdown שהוגדר בגרסה קודמת (הוא ניתק את כל המכשיר). */
    fun fixLockdown() {
        if (android.os.Build.VERSION.SDK_INT < 29 || !dpm.isDeviceOwnerApp(ctx.packageName)) return
        runCatching {
            if (dpm.getAlwaysOnVpnPackage(admin) == ctx.packageName && dpm.isAlwaysOnVpnLockdownEnabled(admin)) {
                dpm.setAlwaysOnVpnPackage(admin, ctx.packageName, false)
                EventLog.info(ctx, "Firewall", "Lockdown הוסר — אפליקציות מורשות גולשות שוב")
            }
        }
    }

    /** בנייה מחדש של המנהרה (למשל אחרי שינוי באפליקציות המורשות). */
    fun rebuild() { if (isRunning()) startService(FirewallVpnService.ACTION_START) }

    data class Counts(val allowed: Int, val blocked: Int)

    fun counts(): Counts {
        val store = AppPolicyStore(ctx)
        val userApps = AppRepository(ctx).installedApps().filter { !it.isSystem }
        val allowed = userApps.count { store.isNetAllowed(it.packageName) }
        return Counts(allowed, userApps.size - allowed)
    }

    private fun startService(action: String) {
        val i = Intent(ctx, FirewallVpnService::class.java).setAction(action)
        ctx.startService(i)
    }
}
