package com.kdroid.filter.devadmin

import android.app.admin.DevicePolicyManager
import android.content.Context
import com.kdroid.filter.admin.AdminReceiver
import com.kdroid.filter.log.EventLog
import com.kdroid.filter.root.RootUtil

/**
 * בדיקת מצב Device Owner והפעלתו דרך Root.
 * המצב נבדק מול ה-API האמיתי בכל פעם — בלי "הצלחה מדומה".
 */
class DeviceOwnerManager(private val ctx: Context) {

    private val dpm =
        ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    val adbCommand = "adb shell dpm set-device-owner com.kdroid.filter/.admin.AdminReceiver"

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(ctx.packageName)

    fun isAdminActive(): Boolean = dpm.isAdminActive(AdminReceiver.component(ctx))

    data class ActivationResult(val ok: Boolean, val message: String)

    /**
     * ניסיון הפיכה ל-Device Owner דרך Root.
     * מריץ dpm set-device-owner כ-root ואז מאמת מול ה-API.
     * מחזיר תוצאה אמיתית — לא מבטיח הצלחה אם ה-API לא אישר.
     */
    fun activateViaRoot(): ActivationResult {
        if (isDeviceOwner()) return ActivationResult(true, "כבר Device Owner")
        if (!RootUtil.isSuPresent()) {
            return ActivationResult(false, "לא זוהתה גישת Root במכשיר")
        }
        val sh = RootUtil.run("dpm set-device-owner com.kdroid.filter/.admin.AdminReceiver")
        // אימות מצב בפועל, לא לפי פלט הפקודה בלבד
        val now = isDeviceOwner()
        return if (now) {
            EventLog.info(ctx, "DeviceOwner", "הופעל דרך Root")
            ActivationResult(true, "Device Owner הופעל בהצלחה")
        } else {
            val reason = (sh.err.ifBlank { sh.out }).ifBlank { "הפקודה לא הצליחה" }
            EventLog.error(ctx, "DeviceOwner", "כשל הפעלה דרך Root: $reason")
            ActivationResult(
                false,
                "לא ניתן היה להפוך ל-Device Owner דרך Root במכשיר הזה.\n$reason\n" +
                    "ייתכן שכבר קיים חשבון במכשיר. נסה דרך ADB על מכשיר נקי."
            )
        }
    }
}
