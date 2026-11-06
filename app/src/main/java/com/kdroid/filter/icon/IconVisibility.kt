package com.kdroid.filter.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.kdroid.filter.log.EventLog
import com.kdroid.filter.security.Prefs

/**
 * הסתרה/הצגה של אייקון המגירה.
 * ההסתרה מכבה את ה-activity-alias בלבד — האפליקציה עצמה ממשיכה לפעול.
 * כניסה כשהאייקון מוסתר: קוד חייגן *#*#5374#*#* (ראה SecretCodeReceiver),
 * או ADB:  adb shell am start -n com.kdroid.filter/.MainActivity
 */
object IconVisibility {

    private fun alias(ctx: Context) =
        ComponentName(ctx.packageName, "com.kdroid.filter.LauncherAlias")

    fun isHidden(ctx: Context): Boolean {
        val state = ctx.packageManager.getComponentEnabledSetting(alias(ctx))
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun setHidden(ctx: Context, hidden: Boolean) {
        val newState = if (hidden)
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        ctx.packageManager.setComponentEnabledSetting(
            alias(ctx), newState, PackageManager.DONT_KILL_APP
        )
        Prefs(ctx).iconHidden = hidden
        EventLog.info(ctx, "IconVisibility", if (hidden) "אייקון הוסתר" else "אייקון הוצג")
    }
}
