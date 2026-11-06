package com.kdroid.filter.filter

import android.content.Context

/**
 * רשימות סינון דומיינים.
 * listMode = BLOCK: הכול מותר חוץ מהרשימה השחורה.
 * listMode = ALLOW: הכול חסום חוץ מהרשימה הלבנה (סינון קשיח).
 * בנוסף רשימת דומיינים מותרים לכל אפליקציה (לחסימת דפדפן פנימי לפי דומיין).
 */
class DomainStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("kdroid_dns", Context.MODE_PRIVATE)

    enum class ListMode { BLOCK, ALLOW }

    var listMode: ListMode
        get() = if (sp.getString("mode", "BLOCK") == "ALLOW") ListMode.ALLOW else ListMode.BLOCK
        set(v) = sp.edit().putString("mode", v.name).apply()

    var upstreamDns: String
        get() = sp.getString("upstream", "1.1.1.1")!!
        set(v) = sp.edit().putString("upstream", v).apply()

    fun blocked(): Set<String> = sp.getStringSet("blocked", emptySet())!!
    fun allowed(): Set<String> = sp.getStringSet("allowed", emptySet())!!

    fun setBlockedAll(items: Collection<String>) =
        sp.edit().putStringSet("blocked", items.map { norm(it) }.toSet()).apply()
    fun setAllowedAll(items: Collection<String>) =
        sp.edit().putStringSet("allowed", items.map { norm(it) }.toSet()).apply()

    fun addBlocked(d: String) = add("blocked", d)
    fun removeBlocked(d: String) = remove("blocked", d)
    fun addAllowed(d: String) = add("allowed", d)
    fun removeAllowed(d: String) = remove("allowed", d)

    /** רשימת דומיינים מותרים לאפליקציה מסוימת (אם ריקה — אין הגבלת דומיין ייעודית). */
    fun appAllowed(pkg: String): Set<String> = sp.getStringSet("app_$pkg", emptySet())!!
    fun addAppAllowed(pkg: String, d: String) = add("app_$pkg", d)
    fun removeAppAllowed(pkg: String, d: String) = remove("app_$pkg", d)

    private fun norm(d: String) = d.trim().lowercase().removePrefix("www.")

    private fun add(key: String, d: String) {
        val s = sp.getStringSet(key, emptySet())!!.toMutableSet()
        s.add(norm(d)); sp.edit().putStringSet(key, s).apply()
    }

    private fun remove(key: String, d: String) {
        val s = sp.getStringSet(key, emptySet())!!.toMutableSet()
        s.remove(norm(d)); sp.edit().putStringSet(key, s).apply()
    }

    /** האם host תואם ערך ברשימה (התאמה מדויקת או תת-דומיין). */
    fun matches(host: String, list: Set<String>): Boolean {
        val h = norm(host)
        return list.any { d -> h == d || h.endsWith(".$d") }
    }
}
