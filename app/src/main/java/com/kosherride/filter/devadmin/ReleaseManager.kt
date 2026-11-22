package com.kosherride.filter.devadmin

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.kosherride.filter.admin.AdminReceiver
import com.kosherride.filter.log.EventLog

/**
 * הסרת ניהול: מחזיר את המכשיר למצב שלפני ההתקנה ומסיר את האפליקציה מ-Device Owner.
 * כל שלב רץ בנפרד — כישלון בשלב אחד לא עוצר את השאר.
 */
object ReleaseManager {

    data class Result(val ok: Boolean, val message: String)

    fun release(ctx: Context): Result {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = AdminReceiver.component(ctx)
        val me = ctx.packageName
        if (!dpm.isDeviceOwnerApp(me)) return Result(false, "האפליקציה אינה Device Owner — אין ניהול להסיר")

        fun step(name: String, block: () -> Unit) {
            runCatching(block).onFailure { EventLog.warn(ctx, "Release", "$name: ${it.message}") }
        }

        // 1. קיוסק ו-Launcher
        step("קיוסק") { com.kosherride.filter.kiosk.KioskController(ctx).disable() }
        step("Lock task") { dpm.setLockTaskPackages(admin, emptyArray()) }
        step("Launcher") { dpm.clearPackagePersistentPreferredActivities(admin, me) }

        // 2. חומת אש וסמל הכשרות
        step("חומת אש") { com.kosherride.filter.firewall.FirewallController(ctx).disable() }
        step("Always-on VPN") { dpm.setAlwaysOnVpnPackage(admin, null, false) }
        step("סמל כשרות") { com.kosherride.filter.watermark.WatermarkController.setEnabled(ctx, false) }

        // 3. כל ההגבלות
        val rm = com.kosherride.filter.restrictions.RestrictionsManager(ctx)
        com.kosherride.filter.restrictions.RestrictionsManager.all().forEach { def ->
            step(def.title) { rm.apply(def, false) }
        }
        step("נעילת VPN") { dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_CONFIG_VPN) }
        step("שורת מצב") { dpm.setStatusBarDisabled(admin, false) }
        step("מסך נעילה") { dpm.setKeyguardDisabled(admin, false) }
        step("מצלמה") { dpm.setCameraDisabled(admin, false) }
        step("צילום מסך") { dpm.setScreenCaptureDisabled(admin, false) }

        // 4. אפליקציות: ביטול הסתרה, השעיה וחסימת הסרה
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 24) PackageManager.MATCH_UNINSTALLED_PACKAGES else 0
        val pkgs = runCatching { pm.getInstalledApplications(flags).map { it.packageName } }.getOrDefault(emptyList())
        pkgs.forEach { p ->
            step("unhide $p") { if (dpm.isApplicationHidden(admin, p)) dpm.setApplicationHidden(admin, p, false) }
            step("uninstall $p") { if (dpm.isUninstallBlocked(admin, p)) dpm.setUninstallBlocked(admin, p, false) }
        }
        step("השעיה") { dpm.setPackagesSuspended(admin, pkgs.toTypedArray(), false) }

        // 5. אייקון האפליקציה חוזר להופיע
        step("אייקון") { com.kosherride.filter.icon.IconVisibility.setHidden(ctx, false) }

        // 6. הסרת Device Owner — אחרי זה אפשר להסיר את האפליקציה כרגיל
        return try {
            dpm.clearDeviceOwnerApp(me)
            EventLog.info(ctx, "Release", "הניהול הוסר — המכשיר חזר למצב רגיל")
            Result(true, "הניהול הוסר. המכשיר חזר למצב רגיל, ועכשיו אפשר להסיר את האפליקציה.")
        } catch (e: Exception) {
            EventLog.error(ctx, "Release", "clearDeviceOwnerApp: ${e.message}")
            Result(false, "ההגבלות בוטלו, אבל הסרת ה-Device Owner נכשלה: ${e.message}")
        }
    }
}
