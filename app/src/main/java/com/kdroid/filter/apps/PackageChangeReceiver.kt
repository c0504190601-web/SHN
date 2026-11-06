package com.kdroid.filter.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kdroid.filter.log.EventLog

/**
 * אפליקציה חדשה שמותקנת לאחר ההגדרה נשארת חסומה לרשת כברירת מחדל
 * ומסומנת "ממתינה לאישור" עד שהמנהל מאשר אותה.
 */
class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                // לא מסמנים כ"ידועה": כך isPending יחזיר true עד אישור מפורש.
                EventLog.warn(context, "PackageChange", "אפליקציה חדשה ממתינה לאישור: $pkg")
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                EventLog.info(context, "PackageChange", "אפליקציה הוסרה: $pkg")
            }
        }
    }
}
