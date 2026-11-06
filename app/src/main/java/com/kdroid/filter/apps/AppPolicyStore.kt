package com.kdroid.filter.apps

import android.content.Context

/**
 * העדפות מדיניות מקומיות לכל אפליקציה (נשמרות, נאכפות בשלבים הבאים):
 * - netAllowed: אילו אפליקציות מורשות לאינטרנט (ברירת מחדל: חסום)
 * - onHome: אילו אפליקציות יוצגו ב-Launcher הקיוסק
 * - inAppBrowserBlocked: חסימת דפדפן פנימי לאפליקציה
 * - known: כל החבילות שנראו בעבר — לזיהוי אפליקציות חדשות
 */
class AppPolicyStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("kdroid_apps", Context.MODE_PRIVATE)

    private fun getSet(key: String): MutableSet<String> =
        sp.getStringSet(key, emptySet())!!.toMutableSet()

    private fun putSet(key: String, v: Set<String>) =
        sp.edit().putStringSet(key, v).apply()

    fun isNetAllowed(pkg: String) = getSet("net_allowed").contains(pkg)
    fun setNetAllowed(pkg: String, allowed: Boolean) = toggle("net_allowed", pkg, allowed)
    fun allowedPackages(): Set<String> = getSet("net_allowed")

    fun isOnHome(pkg: String) = getSet("on_home").contains(pkg)
    fun setOnHome(pkg: String, on: Boolean) = toggle("on_home", pkg, on)

    fun isInAppBrowserBlocked(pkg: String) = getSet("browser_blocked").contains(pkg)
    fun setInAppBrowserBlocked(pkg: String, blocked: Boolean) = toggle("browser_blocked", pkg, blocked)

    /** אתחול רשימת ה"ידועות" פעם אחת, בתום ההתקנה. */
    fun seedKnownIfEmpty(all: Set<String>) {
        if (sp.getBoolean("known_seeded", false)) return
        putSet("known", all)
        sp.edit().putBoolean("known_seeded", true).apply()
    }

    fun knownPackages(): Set<String> = getSet("known")
    fun markKnown(pkg: String) = toggle("known", pkg, true)

    /** אפליקציה חדשה = מותקנת ואינה ברשימת הידועות ⇒ חסומה עד אישור. */
    fun isPending(pkg: String): Boolean =
        sp.getBoolean("known_seeded", false) && !knownPackages().contains(pkg)

    /** אישור אפליקציה ממתינה: מסמן כידועה (האישור לרשת נפרד ומפורש). */
    fun approve(pkg: String) = markKnown(pkg)

    private fun toggle(key: String, pkg: String, on: Boolean) {
        val s = getSet(key)
        if (on) s.add(pkg) else s.remove(pkg)
        putSet(key, s)
    }
}
