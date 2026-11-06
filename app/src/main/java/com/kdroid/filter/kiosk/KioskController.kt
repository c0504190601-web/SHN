package com.kdroid.filter.kiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.kdroid.filter.admin.AdminReceiver
import com.kdroid.filter.apps.AppPolicyStore
import com.kdroid.filter.log.EventLog
import com.kdroid.filter.security.Prefs

/**
 * ניהול מצב קיוסק:
 * - הגדרת KioskActivity כמסך הבית (Launcher) דרך Device Owner, בלי שאלה למשתמש.
 * - הגדרת רשימת אפליקציות מותרות ל-Lock Task.
 * - הדלקה/כיבוי שמורים ומוחלים גם לאחר אתחול.
 */
class KioskController(private val ctx: Context) {

    private val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = AdminReceiver.component(ctx)
    private val prefs = Prefs(ctx)
    private val store = AppPolicyStore(ctx)

    fun isDeviceOwner() = dpm.isDeviceOwnerApp(ctx.packageName)
    fun isEnabled() = prefs.kioskEnabled

    /** אפליקציית הניהול + האפליקציות שסומנו למסך הבית. */
    fun lockTaskPackages(): Array<String> =
        (setOf(ctx.packageName) + store.homePackages()).toTypedArray()

    fun refreshLockTaskPackages() {
        if (isDeviceOwner() && isEnabled()) {
            runCatching { dpm.setLockTaskPackages(admin, lockTaskPackages()) }
        }
    }

    fun enable(): String {
        prefs.kioskEnabled = true
        if (!isDeviceOwner()) {
            EventLog.warn(ctx, "Kiosk", "קיוסק הופעל ללא Device Owner — נעילה חלקית בלבד")
            return "קיוסק הופעל, אך ללא Device Owner הנעילה חלקית"
        }
        return try {
            dpm.setLockTaskPackages(admin, lockTaskPackages())
            val f = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                admin, f, ComponentName(ctx, KioskActivity::class.java)
            )
            EventLog.info(ctx, "Kiosk", "קיוסק הופעל והוגדר כמסך הבית")
            "קיוסק פעיל — המכשיר ייפתח למסך הקיוסק"
        } catch (e: Exception) {
            EventLog.error(ctx, "Kiosk", "כשל הפעלת קיוסק: ${e.message}")
            "שגיאה בהפעלת קיוסק: ${e.message}"
        }
    }

    fun disable(): String {
        prefs.kioskEnabled = false
        if (isDeviceOwner()) {
            runCatching { dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName) }
            runCatching { dpm.setLockTaskPackages(admin, arrayOf(ctx.packageName)) }
        }
        EventLog.info(ctx, "Kiosk", "קיוסק כובה")
        return "קיוסק כובה — מסך הבית הרגיל חוזר"
    }

    fun openKiosk() {
        ctx.startActivity(
            Intent(ctx, KioskActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
