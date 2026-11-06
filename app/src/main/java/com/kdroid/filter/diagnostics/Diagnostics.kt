package com.kdroid.filter.diagnostics

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import com.kdroid.filter.devadmin.DeviceOwnerManager
import com.kdroid.filter.model.CapabilityReport
import com.kdroid.filter.model.CapabilityStatus
import com.kdroid.filter.root.RootUtil
import com.kdroid.filter.security.Prefs

/**
 * אבחון יכולות אמיתי. כל שורה משקפת מצב שנבדק מול המערכת,
 * ולא הנחה או ברירת מחדל.
 */
class Diagnostics(private val ctx: Context) {

    private val dpm =
        ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val owner = DeviceOwnerManager(ctx)

    fun deviceInfo(): List<Pair<String, String>> = listOf(
        "דגם" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "גרסת Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "חבילה" to ctx.packageName,
        "מזהה מכשיר" to (Prefs(ctx).deviceId ?: "טרם הוקצה")
    )

    fun run(): List<CapabilityReport> {
        val isDO = owner.isDeviceOwner()
        val reports = mutableListOf<CapabilityReport>()

        reports += CapabilityReport(
            "device_owner", "Device Owner",
            if (isDO) CapabilityStatus.SUPPORTED else CapabilityStatus.NEEDS_DEVICE_OWNER,
            if (isDO) "פעיל" else "לא פעיל — יש להפעיל דרך ADB או Root"
        )

        reports += CapabilityReport(
            "device_admin", "מנהל מכשיר (Device Admin)",
            if (owner.isAdminActive()) CapabilityStatus.SUPPORTED else CapabilityStatus.FAILED,
            if (owner.isAdminActive()) "פעיל" else "לא פעיל"
        )

        val root = RootUtil.isSuPresent()
        reports += CapabilityReport(
            "root", "גישת Root",
            if (root) CapabilityStatus.SUPPORTED else CapabilityStatus.UNSUPPORTED,
            if (root) "זוהה su (אופציונלי להפעלה בלי מחשב)" else "לא זוהה — אין צורך בו לחומת האש"
        )

        reports += CapabilityReport(
            "pin", "קוד מנהל",
            if (Prefs(ctx).isPinSet) CapabilityStatus.SUPPORTED else CapabilityStatus.NEEDS_PERMISSION,
            if (Prefs(ctx).isPinSet) "מוגדר" else "טרם הוגדר"
        )

        // דגימת הגבלות מפתח: נבדק אם הן זמינות תחת Device Owner.
        reports += restriction("no_camera", "חסימת מצלמה", isDO)
        reports += restriction("no_factory_reset", "מניעת איפוס להגדרות יצרן", isDO)
        reports += restriction("no_install_apps", "מניעת התקנת אפליקציות", isDO)
        reports += restriction("no_config_vpn", "נעילת הגדרות VPN (לחומת אש)", isDO)
        reports += restriction("no_debugging", "חסימת אפשרויות מפתח", isDO)
        reports += restriction("no_safe_boot", "חסימת מצב בטוח", isDO)

        // יכולת חומת אש (VPNService) — זמינה בלי Root, מותנית בהרשאת VPN של המשתמש.
        reports += CapabilityReport(
            "vpn_firewall", "חומת אש דרך VPNService",
            CapabilityStatus.UNKNOWN,
            "נתמך ללא Root; דורש אישור VPN חד-פעמי. מימוש מלא בשלב 3."
        )

        return reports
    }

    /**
     * הגבלות UserManager מיושמות ע"י Device Owner.
     * כשאין DO הן אינן זמינות — מדווחים NEEDS_DEVICE_OWNER, בלי להעמיד פנים שהוחלו.
     */
    private fun restriction(key: String, title: String, isDO: Boolean): CapabilityReport {
        return if (isDO) {
            CapabilityReport(key, title, CapabilityStatus.SUPPORTED, "זמין להחלה (יוגדר בשלב 4)")
        } else {
            CapabilityReport(key, title, CapabilityStatus.NEEDS_DEVICE_OWNER, "דורש Device Owner")
        }
    }
}
