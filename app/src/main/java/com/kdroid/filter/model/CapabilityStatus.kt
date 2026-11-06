package com.kdroid.filter.model

/**
 * מצב אמיתי של יכולת/הגבלה במכשיר.
 * אסור להציג SUPPORTED אם ה-API לא אישר בפועל.
 */
enum class CapabilityStatus {
    SUPPORTED,        // פועל בפועל
    FAILED,           // ה-API ניסה והחזיר שגיאה
    NEEDS_DEVICE_OWNER, // דורש Device Owner שאינו פעיל
    NEEDS_ROOT,       // דורש Root שאינו זמין
    NEEDS_PERMISSION, // דורש הרשאת משתמש
    UNSUPPORTED,      // לא נתמך בגרסה/יצרן
    UNKNOWN           // דורש בדיקה
}

data class CapabilityReport(
    val key: String,
    val title: String,
    val status: CapabilityStatus,
    val detail: String = ""
)
