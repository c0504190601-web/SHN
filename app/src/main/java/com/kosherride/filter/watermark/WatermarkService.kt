package com.kosherride.filter.watermark

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.kosherride.filter.R
import com.kosherride.filter.log.EventLog

/**
 * מציג את סמל הכשרות במרכז המסך, מעל כל האפליקציות.
 * החלון שקוף ללחיצות (FLAG_NOT_TOUCHABLE), כך שהסמל לא מפריע לשימוש.
 * רץ כשירות קדמה כדי שהמערכת לא תסגור אותו.
 */
class WatermarkService : Service() {

    private var wm: WindowManager? = null
    private var view: WatermarkView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !WatermarkController.enabled(this)) {
            stopSelf(); return START_NOT_STICKY
        }
        show()
        return START_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "סמל כשרות", NotificationManager.IMPORTANCE_MIN)
                    .apply { setShowBadge(false) }
            )
        }
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("נסיעה כשרה")
            .setContentText("סמל הכשרות מוצג")
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            EventLog.warn(this, "Watermark", "שירות קדמה נכשל: ${e.message}")
        }
    }

    /** גודל ומיקום לפי ההגדרה: במרכז (גדול) או בצד שמאל (קטן). */
    private fun layoutParams(): WindowManager.LayoutParams {
        val dm = resources.displayMetrics
        val left = WatermarkController.position(this) == "left"
        val frac = if (left) 0.18f else 0.42f
        val size = (minOf(dm.widthPixels, dm.heightPixels) * frac).toInt()
        return WindowManager.LayoutParams(
            size, (size * 178f / 132f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Gravity.LEFT (ולא START) — צד שמאל של המסך גם בממשק בעברית
            gravity = if (left) Gravity.LEFT or Gravity.CENTER_VERTICAL else Gravity.CENTER
            x = if (left) (12 * dm.density).toInt() else 0
            alpha = 0.8f // מגבלת המערכת לחלונות שקופים ללחיצות; השקיפות העיקרית בתוך הציור
        }
    }

    private fun show() {
        val existing = view
        if (existing != null) {
            // כבר מוצג — רק מעדכנים מיקום/גודל אם ההגדרה השתנתה
            runCatching { wm?.updateViewLayout(existing, layoutParams()) }
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            EventLog.warn(this, "Watermark", "אין הרשאת 'הצגה מעל אפליקציות' — הסמל לא מוצג")
            stopSelf(); return
        }
        val w = getSystemService(WINDOW_SERVICE) as WindowManager
        val v = WatermarkView(this).apply { alpha = 0.22f } // שקוף יותר
        try {
            w.addView(v, layoutParams())
            wm = w; view = v
            EventLog.info(this, "Watermark", "סמל הכשרות מוצג")
        } catch (e: Exception) {
            EventLog.error(this, "Watermark", "הצגת הסמל נכשלה: ${e.message}")
            stopSelf()
        }
    }

    override fun onDestroy() {
        view?.let { v -> runCatching { wm?.removeView(v) } }
        view = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.kosherride.filter.WATERMARK_STOP"
        private const val CHANNEL = "kosherride_watermark"
        private const val NOTIF_ID = 4201
    }
}
