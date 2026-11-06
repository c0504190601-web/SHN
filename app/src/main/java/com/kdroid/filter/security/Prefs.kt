package com.kdroid.filter.security

import android.content.Context
import android.content.SharedPreferences

/**
 * אחסון מקומי. לא נשמר כאן קוד PIN גלוי — רק גיבוב ו-salt.
 */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.getSharedPreferences("kdroid_prefs", Context.MODE_PRIVATE)

    // --- PIN ---
    var pinHash: String?
        get() = sp.getString("pin_hash", null)
        set(v) = sp.edit().putString("pin_hash", v).apply()

    var pinSalt: String?
        get() = sp.getString("pin_salt", null)
        set(v) = sp.edit().putString("pin_salt", v).apply()

    var pinIterations: Int
        get() = sp.getInt("pin_iter", 0)
        set(v) = sp.edit().putInt("pin_iter", v).apply()

    val isPinSet: Boolean get() = pinHash != null && pinSalt != null

    // --- נעילת ניסיונות ---
    var failedAttempts: Int
        get() = sp.getInt("pin_failed", 0)
        set(v) = sp.edit().putInt("pin_failed", v).apply()

    var lockUntil: Long
        get() = sp.getLong("pin_lock_until", 0L)
        set(v) = sp.edit().putLong("pin_lock_until", v).apply()

    // --- אשף הושלם ---
    var setupDone: Boolean
        get() = sp.getBoolean("setup_done", false)
        set(v) = sp.edit().putBoolean("setup_done", v).apply()

    // --- מזהה מכשיר (מוקצה ע"י השרת בעתיד) ---
    var deviceId: String?
        get() = sp.getString("device_id", null)
        set(v) = sp.edit().putString("device_id", v).apply()

    // --- אייקון מוסתר ---
    var iconHidden: Boolean
        get() = sp.getBoolean("icon_hidden", false)
        set(v) = sp.edit().putBoolean("icon_hidden", v).apply()

    // --- חומת אש ---
    var firewallEnabled: Boolean
        get() = sp.getBoolean("fw_enabled", false)
        set(v) = sp.edit().putBoolean("fw_enabled", v).apply()

    var blockWholeDevice: Boolean
        get() = sp.getBoolean("fw_block_all", false)
        set(v) = sp.edit().putBoolean("fw_block_all", v).apply()

    /** false = מצב חסימה לפי אפליקציה (3א); true = מצב סינון דומיינים (3ב). */
    var filterMode: Boolean
        get() = sp.getBoolean("fw_filter_mode", false)
        set(v) = sp.edit().putBoolean("fw_filter_mode", v).apply()

    // --- קיוסק ---
    var kioskEnabled: Boolean
        get() = sp.getBoolean("kiosk_enabled", false)
        set(v) = sp.edit().putBoolean("kiosk_enabled", v).apply()
}
