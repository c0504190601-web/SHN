package com.kdroid.filter.root

import java.io.DataOutputStream

/**
 * כלי Root. משמש כאן רק להפעלת Device Owner בהתקנה (כשאין מחשב),
 * ולא לסינון רשת. בלי Root האפליקציה עדיין עובדת דרך Device Owner.
 */
object RootUtil {

    data class Shell(val exit: Int, val out: String, val err: String) {
        val ok: Boolean get() = exit == 0
    }

    /** האם קיים בינארי su בנתיב כלשהו. בדיקה זולה, לא מבקשת הרשאה. */
    fun isSuPresent(): Boolean {
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/su/bin/su", "/system/sbin/su", "/vendor/bin/su",
            "/data/local/bin/su", "/data/local/xbin/su"
        )
        return paths.any { java.io.File(it).exists() }
    }

    /** מריץ פקודה כ-root. מחזיר תוצאה אמיתית; אם su נכשל מחזיר exit!=0. */
    fun run(cmd: String): Shell {
        return try {
            val p = Runtime.getRuntime().exec("su")
            DataOutputStream(p.outputStream).use { os ->
                os.writeBytes("$cmd\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            val exit = p.waitFor()
            Shell(exit, out.trim(), err.trim())
        } catch (e: Exception) {
            Shell(-1, "", e.message ?: "su failed")
        }
    }
}
