package com.kdroid.filter.filter

import android.content.Context
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import com.kdroid.filter.apps.AppPolicyStore
import com.kdroid.filter.log.EventLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * מנוע סינון DNS ברמת IPv4/UDP פורט 53.
 * - מפענח את שם המארח מתוך שאילתת ה-DNS.
 * - מחליט לפי מצב הרשימות, רשימת האפליקציה, ומצב החסימה של האפליקציה.
 * - חסימה: מחזיר תשובת NXDOMAIN. אישור: מעביר ל-upstream דרך שקע מוגן ומחזיר את התשובה.
 *
 * הערה: סינון DNS אינו עמיד בפני DoH/DoT או כתובות IP קשיחות; לחסימה קשיחה
 * יש להשתמש במצב החסימה לפי אפליקציה (3א). יש לבדוק על מכשיר אמיתי.
 */
class DnsFilter(
    private val service: VpnService,
    private val ctx: Context
) {
    private val store = DomainStore(ctx)
    private val appStore = AppPolicyStore(ctx)
    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** מקבל מנת IPv4 גולמית; מחזיר מנת תשובה לכתיבה חזרה למנהרה, או null (drop). */
    fun handle(packet: ByteArray, len: Int): ByteArray? {
        try {
            if (len < 28) return null
            if ((packet[0].toInt() ushr 4 and 0xF) != 4) return null // IPv4 בלבד
            val ihl = (packet[0].toInt() and 0x0F) * 4
            if (packet[9].toInt() and 0xFF != 17) return null // UDP בלבד
            val udp = ihl
            val srcPort = ((packet[udp].toInt() and 0xFF) shl 8) or (packet[udp + 1].toInt() and 0xFF)
            val dstPort = ((packet[udp + 2].toInt() and 0xFF) shl 8) or (packet[udp + 3].toInt() and 0xFF)
            if (dstPort != 53) return null
            val dnsStart = udp + 8
            if (dnsStart >= len) return null

            val qname = parseQName(packet, dnsStart, len) ?: return null
            val uid = ownerUid(packet, ihl, srcPort, dstPort)
            val decision = decide(qname, uid)

            return if (decision) {
                val resp = forwardUpstream(packet, dnsStart, len) ?: return null
                buildReply(packet, ihl, srcPort, dstPort, resp)
            } else {
                EventLog.info(ctx, "DNS", "נחסם: $qname")
                buildReply(packet, ihl, srcPort, dstPort, nxdomain(packet, dnsStart, len))
            }
        } catch (e: Exception) {
            return null // drop בשקט; הלקוח ינסה שוב
        }
    }

    /** true = מותר, false = חסום. */
    private fun decide(host: String, uid: Int): Boolean {
        // מיפוי אפליקציה (אם זמין): אפליקציה חסומה לרשת ⇒ אין רזולוציה.
        val pkgs = if (uid > 0) runCatching { ctx.packageManager.getPackagesForUid(uid) }.getOrNull() else null
        if (pkgs != null && pkgs.isNotEmpty()) {
            val anyAllowed = pkgs.any { appStore.isNetAllowed(it) }
            if (!anyAllowed) return false
            // רשימת דומיינים ייעודית לאפליקציה (דפדפן פנימי): אם קיימת, רק היא מותרת.
            for (p in pkgs) {
                val appList = store.appAllowed(p)
                if (appList.isNotEmpty()) return store.matches(host, appList)
            }
        }
        return when (store.listMode) {
            DomainStore.ListMode.BLOCK -> !store.matches(host, store.blocked())
            DomainStore.ListMode.ALLOW -> store.matches(host, store.allowed())
        }
    }

    private fun ownerUid(packet: ByteArray, ihl: Int, srcPort: Int, dstPort: Int): Int {
        if (Build.VERSION.SDK_INT < 29) return -1
        return try {
            val src = InetAddress.getByAddress(packet.copyOfRange(12, 16))
            val dst = InetAddress.getByAddress(packet.copyOfRange(16, 20))
            cm.getConnectionOwnerUid(17, InetSocketAddress(src, srcPort), InetSocketAddress(dst, dstPort))
        } catch (_: Exception) { -1 }
    }

    private fun parseQName(p: ByteArray, dnsStart: Int, len: Int): String? {
        var i = dnsStart + 12 // אחרי כותרת DNS (12 בייט)
        val sb = StringBuilder()
        while (i < len) {
            val l = p[i].toInt() and 0xFF
            if (l == 0) break
            if (l and 0xC0 != 0) return null // מצביע/דחיסה — לא בשאילתה רגילה
            i++
            if (i + l > len) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (j in 0 until l) sb.append((p[i + j].toInt() and 0xFF).toChar())
            i += l
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    private fun forwardUpstream(p: ByteArray, dnsStart: Int, len: Int): ByteArray? {
        val payload = p.copyOfRange(dnsStart, len)
        DatagramSocket().use { sock ->
            service.protect(sock)
            sock.soTimeout = 4000
            val upstream = InetAddress.getByName(store.upstreamDns)
            sock.send(DatagramPacket(payload, payload.size, upstream, 53))
            val buf = ByteArray(1500)
            val resp = DatagramPacket(buf, buf.size)
            sock.receive(resp)
            return buf.copyOfRange(0, resp.length)
        }
    }

    /** הופך את השאילתה לתשובת NXDOMAIN (QR=1, RCODE=3). */
    private fun nxdomain(p: ByteArray, dnsStart: Int, len: Int): ByteArray {
        val dns = p.copyOfRange(dnsStart, len)
        dns[2] = (dns[2].toInt() or 0x80).toByte() // QR=1
        dns[3] = ((dns[3].toInt() and 0xF0) or 0x03).toByte() // RCODE=3 (NXDOMAIN)
        return dns
    }

    /** בונה מנת IPv4/UDP תשובה: היפוך כתובות ופורטים, checksum של IP, UDP checksum=0. */
    private fun buildReply(req: ByteArray, ihl: Int, srcPort: Int, dstPort: Int, dnsResp: ByteArray): ByteArray {
        val udpLen = 8 + dnsResp.size
        val total = ihl + udpLen
        val out = ByteArray(total)
        // כותרת IP מהבקשה
        System.arraycopy(req, 0, out, 0, ihl)
        // אורך כולל
        out[2] = (total ushr 8).toByte(); out[3] = (total and 0xFF).toByte()
        out[8] = 64 // TTL
        // היפוך כתובות מקור/יעד
        for (k in 0 until 4) { val t = out[12 + k]; out[12 + k] = out[16 + k]; out[16 + k] = t }
        // checksum של כותרת IP
        out[10] = 0; out[11] = 0
        val ipck = checksum(out, 0, ihl)
        out[10] = (ipck ushr 8).toByte(); out[11] = (ipck and 0xFF).toByte()
        // UDP: היפוך פורטים
        out[ihl] = (dstPort ushr 8).toByte(); out[ihl + 1] = (dstPort and 0xFF).toByte()
        out[ihl + 2] = (srcPort ushr 8).toByte(); out[ihl + 3] = (srcPort and 0xFF).toByte()
        out[ihl + 4] = (udpLen ushr 8).toByte(); out[ihl + 5] = (udpLen and 0xFF).toByte()
        out[ihl + 6] = 0; out[ihl + 7] = 0 // UDP checksum=0 (אופציונלי ב-IPv4)
        System.arraycopy(dnsResp, 0, out, ihl + 8, dnsResp.size)
        return out
    }

    private fun checksum(b: ByteArray, off: Int, length: Int): Int {
        var sum = 0; var i = off
        while (i < off + length - 1) {
            sum += ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (length % 2 == 1) sum += (b[off + length - 1].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }
}
