package com.kdroid.filter.restrictions

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.UserManager
import com.kdroid.filter.admin.AdminReceiver
import com.kdroid.filter.log.EventLog

/**
 * מנוע הגבלות מערכת. כל הגבלה מוחלת דרך DevicePolicyManager ונבדקת מול המצב האמיתי.
 * דורש Device Owner. הגבלות שאינן נתמכות בגרסה/יצרן מדווחות ככישלון, בלי הצלחה מדומה.
 */
class RestrictionsManager(private val ctx: Context) {

    private val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val um = ctx.getSystemService(Context.USER_SERVICE) as UserManager
    private val admin = AdminReceiver.component(ctx)
    private val sp = ctx.getSharedPreferences("kdroid_restrict", Context.MODE_PRIVATE)

    enum class Type { USER, CAMERA, SCREEN_CAPTURE, STATUS_BAR, KEYGUARD }

    data class Def(val id: String, val key: String, val title: String, val type: Type, val group: String)

    fun byId(id: String): Def? = all().firstOrNull { it.id == id }

    fun isDeviceOwner() = dpm.isDeviceOwnerApp(ctx.packageName)

    data class Result(val ok: Boolean, val message: String)

    fun apply(def: Def, enable: Boolean): Result {
        if (!isDeviceOwner()) return Result(false, "דורש Device Owner")
        return try {
            when (def.type) {
                Type.USER ->
                    if (enable) dpm.addUserRestriction(admin, def.key)
                    else dpm.clearUserRestriction(admin, def.key)
                Type.CAMERA -> dpm.setCameraDisabled(admin, enable)
                Type.SCREEN_CAPTURE -> dpm.setScreenCaptureDisabled(admin, enable)
                Type.STATUS_BAR -> { dpm.setStatusBarDisabled(admin, enable); sp.edit().putBoolean(def.key, enable).apply() }
                Type.KEYGUARD -> { dpm.setKeyguardDisabled(admin, enable); sp.edit().putBoolean(def.key, enable).apply() }
            }
            val applied = isApplied(def)
            if (applied == enable) {
                EventLog.info(ctx, "Restrictions", "${def.title} = $enable")
                Result(true, "הוחל")
            } else {
                EventLog.warn(ctx, "Restrictions", "${def.title}: המערכת לא אישרה")
                Result(false, "המערכת לא החילה (ייתכן שלא נתמך בדגם/גרסה)")
            }
        } catch (e: Exception) {
            EventLog.error(ctx, "Restrictions", "${def.title}: ${e.message}")
            Result(false, "שגיאה/לא נתמך: ${e.message}")
        }
    }

    fun isApplied(def: Def): Boolean = try {
        when (def.type) {
            Type.USER -> um.hasUserRestriction(def.key)
            Type.CAMERA -> dpm.getCameraDisabled(admin)
            Type.SCREEN_CAPTURE -> dpm.getScreenCaptureDisabled(admin)
            Type.STATUS_BAR, Type.KEYGUARD -> sp.getBoolean(def.key, false)
        }
    } catch (_: Exception) { false }

    companion object {
        fun all(): List<Def> = listOf(
            // מכשיר ורשת
            Def("wifi", UserManager.DISALLOW_CONFIG_WIFI, "שינוי הגדרות Wi-Fi", Type.USER, "מכשיר ורשת"),
            Def("bt_cfg", UserManager.DISALLOW_CONFIG_BLUETOOTH, "שינוי Bluetooth", Type.USER, "מכשיר ורשת"),
            Def("bt", UserManager.DISALLOW_BLUETOOTH, "חסימת Bluetooth", Type.USER, "מכשיר ורשת"),
            Def("hotspot", UserManager.DISALLOW_CONFIG_TETHERING, "נקודה חמה (Hotspot)", Type.USER, "מכשיר ורשת"),
            Def("roaming", UserManager.DISALLOW_DATA_ROAMING, "נדידה (Roaming)", Type.USER, "מכשיר ורשת"),
            Def("mobile", UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS, "הגדרות נתונים סלולריים", Type.USER, "מכשיר ורשת"),
            Def("airplane", UserManager.DISALLOW_AIRPLANE_MODE, "מצב טיסה", Type.USER, "מכשיר ורשת"),
            Def("net_reset", UserManager.DISALLOW_NETWORK_RESET, "איפוס הגדרות רשת", Type.USER, "מכשיר ורשת"),
            Def("private_dns", UserManager.DISALLOW_CONFIG_PRIVATE_DNS, "Private DNS", Type.USER, "מכשיר ורשת"),
            Def("vpn", UserManager.DISALLOW_CONFIG_VPN, "הגדרות VPN (נעילת חומת האש)", Type.USER, "מכשיר ורשת"),
            Def("cell_broadcast", UserManager.DISALLOW_CONFIG_CELL_BROADCASTS, "Cell Broadcast", Type.USER, "מכשיר ורשת"),
            Def("nfc_beam", UserManager.DISALLOW_OUTGOING_BEAM, "NFC (שיתוף Beam)", Type.USER, "מכשיר ורשת"),

            // אבטחה ומערכת
            Def("factory_reset", UserManager.DISALLOW_FACTORY_RESET, "איפוס להגדרות יצרן", Type.USER, "אבטחה ומערכת"),
            Def("safe_boot", UserManager.DISALLOW_SAFE_BOOT, "מצב בטוח (Safe Mode)", Type.USER, "אבטחה ומערכת"),
            Def("dev_options", UserManager.DISALLOW_DEBUGGING_FEATURES, "אפשרויות מפתח", Type.USER, "אבטחה ומערכת"),
            Def("unknown_sources", UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, "התקנה ממקורות לא מוכרים", Type.USER, "אבטחה ומערכת"),
            Def("install_apps", UserManager.DISALLOW_INSTALL_APPS, "התקנת אפליקציות", Type.USER, "אבטחה ומערכת"),
            Def("uninstall_apps", UserManager.DISALLOW_UNINSTALL_APPS, "הסרת אפליקציות", Type.USER, "אבטחה ומערכת"),
            Def("apps_control", UserManager.DISALLOW_APPS_CONTROL, "ניהול אפליקציות ע\"י המשתמש", Type.USER, "אבטחה ומערכת"),
            Def("credentials", UserManager.DISALLOW_CONFIG_CREDENTIALS, "תעודות אבטחה", Type.USER, "אבטחה ומערכת"),
            Def("physical_media", UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, "מדיה חיצונית / כרטיס SD", Type.USER, "אבטחה ומערכת"),
            Def("usb", UserManager.DISALLOW_USB_FILE_TRANSFER, "העברת קבצים דרך USB", Type.USER, "אבטחה ומערכת"),
            Def("printing", UserManager.DISALLOW_PRINTING, "הדפסה", Type.USER, "אבטחה ומערכת"),

            // חשבונות ומשתמשים
            Def("add_user", UserManager.DISALLOW_ADD_USER, "הוספת משתמשים", Type.USER, "חשבונות ומשתמשים"),
            Def("user_switch", UserManager.DISALLOW_USER_SWITCH, "החלפת משתמשים", Type.USER, "חשבונות ומשתמשים"),
            Def("accounts", UserManager.DISALLOW_MODIFY_ACCOUNTS, "הוספה/הסרת חשבונות", Type.USER, "חשבונות ומשתמשים"),
            Def("managed_profile", UserManager.DISALLOW_ADD_MANAGED_PROFILE, "פרופיל עבודה", Type.USER, "חשבונות ומשתמשים"),

            // תקשורת ומדיה
            Def("calls", UserManager.DISALLOW_OUTGOING_CALLS, "שיחות יוצאות", Type.USER, "תקשורת ומדיה"),
            Def("sms", UserManager.DISALLOW_SMS, "SMS", Type.USER, "תקשורת ומדיה"),
            Def("location", UserManager.DISALLOW_SHARE_LOCATION, "שיתוף מיקום", Type.USER, "תקשורת ומדיה"),
            Def("camera", "camera", "מצלמה", Type.CAMERA, "תקשורת ומדיה"),
            Def("microphone", UserManager.DISALLOW_UNMUTE_MICROPHONE, "מיקרופון (השתקה)", Type.USER, "תקשורת ומדיה"),

            // ממשק
            Def("screenshot", "screenshot", "צילומי מסך", Type.SCREEN_CAPTURE, "ממשק"),
            Def("statusbar", "statusbar", "שורת מצב והתראות", Type.STATUS_BAR, "ממשק"),
            Def("volume", UserManager.DISALLOW_ADJUST_VOLUME, "שינוי עוצמת קול", Type.USER, "ממשק"),
            Def("wallpaper", UserManager.DISALLOW_SET_WALLPAPER, "שינוי טפט", Type.USER, "ממשק"),
            Def("autofill", UserManager.DISALLOW_AUTOFILL, "Autofill", Type.USER, "ממשק"),
            Def("keyguard", "keyguard", "מסך נעילה (ביטול)", Type.KEYGUARD, "ממשק"),
        )
    }
}
