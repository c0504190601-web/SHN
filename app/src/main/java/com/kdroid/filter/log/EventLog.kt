package com.kdroid.filter.log

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * יומן אירועים ושגיאות פשוט ובטוח. לא נרשמים בו קודי PIN.
 * נשמר כקובץ טקסט ב-filesDir עם קיטום לגודל סביר.
 */
object EventLog {
    private const val FILE = "kdroid_events.log"
    private const val MAX_BYTES = 256 * 1024
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    enum class Level { INFO, WARN, ERROR }

    @Synchronized
    fun add(ctx: Context, level: Level, tag: String, msg: String) {
        try {
            val f = File(ctx.filesDir, FILE)
            if (f.exists() && f.length() > MAX_BYTES) trim(f)
            f.appendText("${fmt.format(Date())} [${level.name}] $tag: $msg\n")
        } catch (_: Exception) {
            // יומן הוא best-effort; לא מפילים פעולה בגללו
        }
    }

    fun info(ctx: Context, tag: String, msg: String) = add(ctx, Level.INFO, tag, msg)
    fun warn(ctx: Context, tag: String, msg: String) = add(ctx, Level.WARN, tag, msg)
    fun error(ctx: Context, tag: String, msg: String) = add(ctx, Level.ERROR, tag, msg)

    fun read(ctx: Context, maxLines: Int = 500): List<String> {
        return try {
            val f = File(ctx.filesDir, FILE)
            if (!f.exists()) emptyList()
            else f.readLines().takeLast(maxLines).asReversed()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun trim(f: File) {
        try {
            val lines = f.readLines()
            f.writeText(lines.takeLast(2000).joinToString("\n") + "\n")
        } catch (_: Exception) {
        }
    }
}
