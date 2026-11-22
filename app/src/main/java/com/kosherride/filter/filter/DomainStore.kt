package com.kosherride.filter.filter

import android.content.Context

/**
 * רשימות סינון דומיינים.
 * listMode = BLOCK: הכול מותר חוץ מהרשימה השחורה.
 * listMode = ALLOW: הכול חסום חוץ מהרשימה הלבנה (סינון קשיח).
 * בנוסף רשימת דומיינים מותרים לכל אפליקציה (לחסימת דפדפן פנימי לפי דומיין).
 */
class DomainStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("kosherride_dns", Context.MODE_PRIVATE)

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

    fun setAppAllowedAll(pkg: String, items: Collection<String>) =
        sp.edit().putStringSet("app_$pkg", items.map { norm(it) }.filter { it.isNotEmpty() }.toSet()).apply()
    fun clearAppAllowed(pkg: String) = sp.edit().remove("app_$pkg").apply()

    // ---- מצב למידה לדפדפן פנימי ----
    /** עד מתי האפליקציה במצב למידה (מילישניות). 0 = לא לומדת. */
    fun learnUntil(pkg: String): Long = sp.getLong("learn_$pkg", 0L)
    fun isLearning(pkg: String): Boolean = System.currentTimeMillis() < learnUntil(pkg)
    fun startLearning(pkg: String, minutes: Int = LEARN_MINUTES) =
        sp.edit().putLong("learn_$pkg", System.currentTimeMillis() + minutes * 60_000L).apply()
    fun stopLearning(pkg: String) = sp.edit().remove("learn_$pkg").apply()
    /** מזהה הבקשה האחרונה מהאתר שכבר טופלה (כדי לא להתחיל למידה פעמיים). */
    fun lastLearnRequest(pkg: String): Long = sp.getLong("learnreq_$pkg", 0L)
    fun setLastLearnRequest(pkg: String, id: Long) = sp.edit().putLong("learnreq_$pkg", id).apply()

    companion object {
        const val LEARN_MINUTES = 5

        /** הדומיין הבסיסי: m.facebook.com → facebook.com, www.ynet.co.il → ynet.co.il */
        fun baseDomain(host: String): String {
            val parts = host.trim().lowercase().trimEnd('.').split('.').filter { it.isNotEmpty() }
            if (parts.size <= 2) return parts.joinToString(".")
            val sld = parts[parts.size - 2]
            val tld = parts.last()
            val keep = if (tld.length == 2 && sld in setOf("co", "org", "net", "gov", "ac", "com", "edu", "muni", "k12")) 3 else 2
            return parts.takeLast(keep).joinToString(".")
        }
    }

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
