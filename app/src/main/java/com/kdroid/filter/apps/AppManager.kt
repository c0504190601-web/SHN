package com.kdroid.filter.apps

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import com.kdroid.filter.admin.AdminReceiver
import com.kdroid.filter.log.EventLog
import com.kdroid.filter.model.AppInfo
import com.kdroid.filter.model.AppPolicyState

/**
 * פעולות מדיניות על אפליקציות.
 * פעולות המערכת (הסתרה/השעיה/מניעת הסרה) דורשות Device Owner — אחרת מוחזר כישלון ברור.
 * מצב הרשת והדפדפן נשמרים מקומית ונאכפים ע"י חומת האש בשלב 3.
 */
class AppManager(private val ctx: Context) {

    private val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = AdminReceiver.component(ctx)
    private val store = AppPolicyStore(ctx)
    private val pm = ctx.packageManager

    fun isDeviceOwner() = dpm.isDeviceOwnerApp(ctx.packageName)

    data class ActionResult(val ok: Boolean, val message: String)

    private fun requireDO(): ActionResult? =
        if (isDeviceOwner()) null else ActionResult(false, "פעולה זו דורשת Device Owner פעיל")

    // --- הסתרה ---
    fun isHidden(pkg: String): Boolean =
        isDeviceOwner() && runCatching { dpm.isApplicationHidden(admin, pkg) }.getOrDefault(false)

    fun setHidden(pkg: String, hidden: Boolean): ActionResult {
        requireDO()?.let { return it }
        return try {
            val applied = dpm.setApplicationHidden(admin, pkg, hidden)
            if (applied) {
                EventLog.info(ctx, "AppManager", "${if (hidden) "הסתרה" else "ביטול הסתרה"}: $pkg")
                ActionResult(true, if (hidden) "האפליקציה הוסתרה" else "ההסתרה בוטלה")
            } else ActionResult(false, "המערכת לא אישרה את הפעולה (ייתכן שהחבילה מוגנת)")
        } catch (e: Exception) {
            ActionResult(false, "שגיאה: ${e.message}")
        }
    }

    // --- השעיה ---
    fun isSuspended(pkg: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 29) pm.isPackageSuspended(pkg) else false
    }.getOrDefault(false)

    fun setSuspended(pkg: String, suspended: Boolean): ActionResult {
        requireDO()?.let { return it }
        return try {
            val failed = dpm.setPackagesSuspended(admin, arrayOf(pkg), suspended)
            if (failed.isEmpty()) {
                EventLog.info(ctx, "AppManager", "${if (suspended) "השעיה" else "ביטול השעיה"}: $pkg")
                ActionResult(true, if (suspended) "האפליקציה הושעתה" else "ההשעיה בוטלה")
            } else ActionResult(false, "לא ניתן היה להשעות חבילה זו")
        } catch (e: Exception) {
            ActionResult(false, "שגיאה: ${e.message}")
        }
    }

    // --- מניעת הסרה ---
    fun isUninstallBlocked(pkg: String): Boolean =
        isDeviceOwner() && runCatching { dpm.isUninstallBlocked(admin, pkg) }.getOrDefault(false)

    fun setUninstallBlocked(pkg: String, blocked: Boolean): ActionResult {
        requireDO()?.let { return it }
        return try {
            dpm.setUninstallBlocked(admin, pkg, blocked)
            val now = dpm.isUninstallBlocked(admin, pkg)
            if (now == blocked) {
                EventLog.info(ctx, "AppManager", "מניעת הסרה=$blocked: $pkg")
                ActionResult(true, if (blocked) "הסרה נחסמה" else "חסימת ההסרה בוטלה")
            } else ActionResult(false, "המערכת לא החילה את השינוי")
        } catch (e: Exception) {
            ActionResult(false, "שגיאה: ${e.message}")
        }
    }

    // --- מדיניות מקומית (נאכפת בשלב 3) ---
    fun setNetAllowed(pkg: String, allowed: Boolean) {
        store.setNetAllowed(pkg, allowed)
        store.markKnown(pkg)
        EventLog.info(ctx, "AppManager", "אינטרנט=${if (allowed) "מותר" else "חסום"}: $pkg")
    }

    fun setOnHome(pkg: String, on: Boolean) = store.setOnHome(pkg, on)
    fun setInAppBrowserBlocked(pkg: String, blocked: Boolean) = store.setInAppBrowserBlocked(pkg, blocked)
    fun approve(pkg: String) = store.approve(pkg)

    /** בונה מצב מלא לאפליקציה — מיזוג מצב מערכת אמיתי והעדפות מקומיות. */
    fun stateOf(info: AppInfo): AppPolicyState = AppPolicyState(
        info = info,
        hidden = isHidden(info.packageName),
        suspended = isSuspended(info.packageName),
        uninstallBlocked = isUninstallBlocked(info.packageName),
        netAllowed = store.isNetAllowed(info.packageName),
        onHome = store.isOnHome(info.packageName),
        inAppBrowserBlocked = store.isInAppBrowserBlocked(info.packageName),
        pendingApproval = store.isPending(info.packageName)
    )
}
