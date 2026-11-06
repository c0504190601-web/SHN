package com.kdroid.filter.security

import android.content.Context
import android.util.Base64
import com.kdroid.filter.log.EventLog
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * ניהול קוד מנהל:
 * - גיבוב PBKDF2-HMAC-SHA256 עם salt אקראי לכל קוד.
 * - הגבלת ניסיונות עם השהיה מתגברת.
 * - הקוד עצמו לעולם לא נשמר ולא נרשם ביומן.
 */
class PinManager(private val ctx: Context) {

    private val prefs = Prefs(ctx)

    companion object {
        private const val ITERATIONS = 120_000
        private const val KEY_LEN = 256
        private const val SALT_LEN = 16
        private const val MAX_BEFORE_DELAY = 5
        private const val BASE_DELAY_MS = 30_000L // 30 שניות, מוכפל בכל סבב
        private const val TAG = "PinManager"
        const val MIN_PIN_LEN = 4
    }

    val isPinSet: Boolean get() = prefs.isPinSet

    fun setPin(pin: String): Boolean {
        if (pin.length < MIN_PIN_LEN) return false
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt, ITERATIONS)
        prefs.pinSalt = b64(salt)
        prefs.pinHash = b64(hash)
        prefs.pinIterations = ITERATIONS
        prefs.failedAttempts = 0
        prefs.lockUntil = 0L
        EventLog.info(ctx, TAG, "קוד מנהל הוגדר/שונה")
        return true
    }

    /** מחזיר מספר אלפיות שנותרו לנעילה, או 0 אם מותר לנסות. */
    fun lockRemainingMs(): Long {
        val remain = prefs.lockUntil - System.currentTimeMillis()
        return if (remain > 0) remain else 0
    }

    sealed class Result {
        object Success : Result()
        data class Wrong(val attempts: Int) : Result()
        data class Locked(val remainingMs: Long) : Result()
        object NotSet : Result()
    }

    fun verify(pin: String): Result {
        if (!isPinSet) return Result.NotSet
        val remain = lockRemainingMs()
        if (remain > 0) return Result.Locked(remain)

        val salt = b64d(prefs.pinSalt!!)
        val expected = prefs.pinHash!!
        val iter = if (prefs.pinIterations > 0) prefs.pinIterations else ITERATIONS
        val actual = b64(pbkdf2(pin, salt, iter))

        return if (constantEquals(actual, expected)) {
            prefs.failedAttempts = 0
            prefs.lockUntil = 0L
            EventLog.info(ctx, TAG, "כניסת מנהל הצליחה")
            Result.Success
        } else {
            val attempts = prefs.failedAttempts + 1
            prefs.failedAttempts = attempts
            EventLog.warn(ctx, TAG, "ניסיון כניסה שגוי ($attempts)")
            if (attempts >= MAX_BEFORE_DELAY) {
                val rounds = attempts - MAX_BEFORE_DELAY + 1
                val delay = BASE_DELAY_MS * rounds
                prefs.lockUntil = System.currentTimeMillis() + delay
                Result.Locked(delay)
            } else {
                Result.Wrong(attempts)
            }
        }
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iter, KEY_LEN)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return f.generateSecret(spec).encoded
    }

    private fun constantEquals(a: String, b: String): Boolean {
        val ba = a.toByteArray(); val bb = b.toByteArray()
        if (ba.size != bb.size) return false
        var r = 0
        for (i in ba.indices) r = r or (ba[i].toInt() xor bb[i].toInt())
        return r == 0
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun b64d(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
