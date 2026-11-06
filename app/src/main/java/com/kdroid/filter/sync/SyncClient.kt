package com.kdroid.filter.sync

import android.content.Context
import com.kdroid.filter.log.EventLog
import com.kdroid.filter.security.Prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * לקוח רישום וסנכרון מול שרת הניהול. משתמש ב-HttpURLConnection (ללא תלות חיצונית).
 * כל התקשורת אמורה לרוץ מעל HTTPS.
 */
class SyncClient(private val ctx: Context) {

    private val prefs = Prefs(ctx)

    data class EnrollResult(val ok: Boolean, val message: String, val deviceId: String? = null, val linkCode: String? = null)

    /** רישום ראשוני מול השרת; שומר אסימון, מזהה וקוד קישור. */
    fun enroll(serverUrl: String): EnrollResult {
        return try {
            val base = serverUrl.trimEnd('/')
            prefs.serverUrl = base
            val body = PolicyMapper.deviceStatus(ctx)
            val resp = request("POST", "$base/api/device/enroll", body, auth = false)
                ?: return EnrollResult(false, "אין תגובה מהשרת")
            val token = resp.optString("token", "")
            val id = resp.optInt("deviceId", 0)
            val link = resp.optString("linkCode", "")
            if (token.isBlank()) return EnrollResult(false, "השרת לא החזיר אסימון")
            prefs.deviceToken = token
            prefs.deviceId = id.toString()
            prefs.linkCode = link
            prefs.lastAppliedVersion = 0
            EventLog.info(ctx, "Sync", "המכשיר נרשם לשרת (#$id)")
            EnrollResult(true, "נרשם בהצלחה", id.toString(), link)
        } catch (e: Exception) {
            EventLog.error(ctx, "Sync", "כשל רישום: ${e.message}")
            EnrollResult(false, "שגיאה: ${e.message}")
        }
    }

    /** מחזור סנכרון: דיווח סטטוס, משיכת מדיניות והחלתה, דיווח הגרסה שהוחלה. */
    fun sync(): Boolean {
        val base = prefs.serverUrl ?: return false
        val token = prefs.deviceToken ?: return false
        return try {
            request("POST", "$base/api/device/status", PolicyMapper.deviceStatus(ctx), token = token)

            val applied = prefs.lastAppliedVersion
            val pulled = request("GET", "$base/api/device/sync?applied=$applied", null, token = token)
                ?: return false
            val version = pulled.optInt("version", applied)
            if (version > applied) {
                val policy = pulled.optJSONObject("policy") ?: JSONObject()
                PolicyMapper.apply(ctx, policy)
                prefs.lastAppliedVersion = version
                // דיווח הגרסה שהוחלה
                request("GET", "$base/api/device/sync?applied=$version", null, token = token)
            }
            prefs.lastSyncAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            EventLog.warn(ctx, "Sync", "סנכרון נכשל: ${e.message}")
            false
        }
    }

    /** דחיפת המצב המקומי הנוכחי כמדיניות חדשה לשרת. */
    fun pushLocal(): Boolean {
        val base = prefs.serverUrl ?: return false
        val token = prefs.deviceToken ?: return false
        return try {
            val body = JSONObject().put("policy", PolicyMapper.toJson(ctx))
            val r = request("PUT", "$base/api/device/policy", body, token = token) ?: return false
            prefs.lastAppliedVersion = r.optInt("version", prefs.lastAppliedVersion)
            EventLog.info(ctx, "Sync", "המצב המקומי נדחף לשרת")
            true
        } catch (e: Exception) {
            EventLog.warn(ctx, "Sync", "דחיפה נכשלה: ${e.message}")
            false
        }
    }

    private fun request(method: String, url: String, body: JSONObject?, token: String? = null, auth: Boolean = true): JSONObject? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 15000
            if (auth && token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                EventLog.warn(ctx, "Sync", "HTTP $code ל-$url")
                return null
            }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
