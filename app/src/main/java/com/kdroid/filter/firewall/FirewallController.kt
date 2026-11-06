package com.kdroid.filter.firewall

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.kdroid.filter.admin.AdminReceiver
import com.kdroid.filter.apps.AppPolicyStore
import com.kdroid.filter.apps.AppRepository
import com.kdroid.filter.log.EventLog
import com.kdroid.filter.security.Prefs

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
                dpm.setAlwaysOnVpnPackage(admin, ctx.packageName, true)
                EventLog.info(ctx, "Firewall", "הוגדר Always-on VPN עם נעילה")
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
