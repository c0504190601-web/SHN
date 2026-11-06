package com.kdroid.filter.icon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kdroid.filter.MainActivity
import com.kdroid.filter.log.EventLog

/**
 * פותח את האפליקציה בהקשה על קוד חייגן סודי: *#*#5374#*#*
 * שימושי כשאייקון המגירה מוסתר. עדיין נדרש קוד מנהל במסך הכניסה.
 * הערה: חלק מהמכשירים/גרסאות מגבילים שידור SECRET_CODE — יש לבדוק לפי דגם.
 */
class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SECRET_CODE") {
            EventLog.info(context, "SecretCode", "כניסה דרך קוד חייגן")
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(open)
        }
    }
}
