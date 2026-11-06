package com.kdroid.filter.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kdroid.filter.log.EventLog

/**
 * לאחר אתחול: בשלב 1 רק נרשם ביומן ונטענת המדיניות האחרונה התקינה.
 * הפעלת שירותי רקע (חומת אש/קיוסק) תתווסף בשלבים הבאים.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            EventLog.info(context, "Boot", "התקבל $a — טעינת מדיניות אחרונה")
            // TODO שלב 3/4: הפעלת שירות חומת אש וקיוסק בהתאם למדיניות שמורה
        }
    }
}
