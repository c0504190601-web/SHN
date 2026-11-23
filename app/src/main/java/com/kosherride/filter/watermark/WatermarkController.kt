package com.kosherride.filter.watermark

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.kosherride.filter.log.EventLog
import com.kosherride.filter.root.RootUtil

/** הפעלה/כיבוי של סמל הכשרות הקבוע. */
object WatermarkController {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("kosherride_prefs", Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = sp(ctx).getBoolean("watermark_enabled", false)

    fun setEnabled(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean("watermark_enabled", on).apply()
        sync(ctx)
    }

    /** מיקום הסמל: "center" (במרכז, גדול) או "left" (בצד שמאל, קטן). */
    fun position(ctx: Context): String = sp(ctx).getString("watermark_pos", "center") ?: "center"

    fun setPosition(ctx: Context, pos: String) {
        val v = if (pos == "left") "left" else "center"
        if (v == position(ctx)) return
        sp(ctx).edit().putString("watermark_pos", v).apply()
        sync(ctx)
    }

    fun hasPermission(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /** מסך ההגדרות של "הצגה מעל אפליקציות אחרות" עבור האפליקציה. */
    fun permissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** במכשיר עם Root אפשר לתת את ההרשאה בלי לגעת בהגדרות. */
    fun grantViaRoot(ctx: Context): Boolean {
        if (!RootUtil.isSuPresent()) return false
        val ok = RootUtil.run("appops set ${ctx.packageName} SYSTEM_ALERT_WINDOW allow").ok
        EventLog.info(ctx, "Watermark", if (ok) "הרשאת שכבה ניתנה דרך Root" else "מתן הרשאה דרך Root נכשל")
        return ok
    }

    /** מיישם את המצב השמור: מציג או מסיר את הסמל. */
    fun sync(ctx: Context) {
        val i = Intent(ctx, WatermarkService::class.java)
        if (enabled(ctx) && hasPermission(ctx)) {
            runCatching { ctx.startForegroundService(i) }
                .onFailure { EventLog.warn(ctx, "Watermark", "הפעלת הסמל נכשלה: ${it.message}") }
        } else {
            runCatching { ctx.stopService(i) }
        }
    }
}
