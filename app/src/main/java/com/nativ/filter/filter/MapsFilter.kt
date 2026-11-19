package com.nativ.filter.filter

import android.content.Context

/**
 * סינון תמונות וסרטונים בגוגל מפות.
 *
 * עובד בתוך מנוע ה-DNS הקיים (DnsFilter), ולכן פעיל רק כשחומת האש במצב "סינון דומיינים".
 * שאילתות של אפליקציית המפות לשרתי תמונות / סרטונים / Street View מקבלות NXDOMAIN,
 * והמפה, החיפוש והניווט ממשיכים לעבוד.
 *
 * באנדרואיד 10 ומעלה הסינון חל רק על אפליקציית המפות (זיהוי לפי UID).
 * בגרסאות ישנות יותר אין אפשרות לזהות איזו אפליקציה שאלה — ולכן הרשימה חלה על כל המכשיר.
 */
class MapsFilter(ctx: Context) {
    private val sp = ctx.getSharedPreferences("nativ_dns", Context.MODE_PRIVATE)

    companion object {
        val PACKAGES = setOf("com.google.android.apps.maps", "com.google.android.apps.mapslite")

        /** שרתי התמונות, הסרטונים ו-Street View. כל תת-דומיין נחסם גם הוא. */
        val BUILTIN = setOf(
            "googleusercontent.com",              // תמונות מקומות, תמונות משתמשים, סרטונים
            "ggpht.com",                          // תמונות ו-Street View
            "googlevideo.com",                    // סרטונים
            "streetviewpixels-pa.googleapis.com",
            "cbk0.google.com", "cbk1.google.com", "cbk2.google.com", "cbk3.google.com",
            "lh3.google.com", "lh4.google.com", "lh5.google.com", "lh6.google.com",
            "photos.google.com", "video.google.com"
        )

        fun clean(raw: String): String {
            var s = raw.trim().lowercase()
            s = s.removePrefix("http://").removePrefix("https://")
            s = s.substringBefore('/')
            while (s.startsWith("*.") || s.startsWith(".")) s = s.removePrefix("*").removePrefix(".")
            return s.trimEnd('.')
        }
    }

    var enabled: Boolean
        get() = sp.getBoolean("maps_filter", false)
        set(v) = sp.edit().putBoolean("maps_filter", v).apply()

    /** כתובות נוספות שהמנהל הוסיף (למקרה שגוגל מחליפים שרת). */
    fun extra(): Set<String> = sp.getStringSet("maps_extra", emptySet())!!
    fun setExtraAll(items: Collection<String>) =
        sp.edit().putStringSet("maps_extra", items.map { clean(it) }.filter { it.isNotEmpty() }.toSet()).apply()
    fun addExtra(d: String) { val c = clean(d); if (c.isNotEmpty()) setExtraAll(extra() + c) }
    fun removeExtra(d: String) = setExtraAll(extra() - d)

    fun isMapsApp(pkgs: Array<String>?): Boolean = pkgs != null && pkgs.any { it in PACKAGES }

    /** האם יש לחסום את host עבור אפליקציית המפות. */
    fun blocks(host: String): Boolean {
        if (!enabled) return false
        val h = host.trim().lowercase().trimEnd('.')
        return (BUILTIN + extra()).any { d -> h == d || h.endsWith(".$d") }
    }
}
