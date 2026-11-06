package com.kdroid.filter.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import com.kdroid.filter.log.EventLog

class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: android.content.Intent) {
        EventLog.info(context, "AdminReceiver", "מנהל מכשיר הופעל")
    }

    override fun onDisabled(context: Context, intent: android.content.Intent) {
        EventLog.warn(context, "AdminReceiver", "מנהל מכשיר בוטל")
    }

    companion object {
        fun component(ctx: Context): ComponentName =
            ComponentName(ctx.packageName, AdminReceiver::class.java.name)
    }
}
