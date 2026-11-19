package com.nativ.filter.sync

import android.content.Context
import android.os.Build
import com.nativ.filter.filter.DomainStore
import com.nativ.filter.filter.MapsFilter
import com.nativ.filter.firewall.FirewallController
import com.nativ.filter.kiosk.KioskController
import com.nativ.filter.log.EventLog
import com.nativ.filter.restrictions.RestrictionsManager
import com.nativ.filter.security.Prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * תרגום בין מצב המכשיר לבין מבנה המדיניות (JSON) המשותף עם השרת והאתר.
 */
object PolicyMapper {

    /** בונה את מבנה המדיניות הנוכחי מהמכשיר (לדחיפה לשרת). */
    fun toJson(ctx: Context): JSONObject {
        val prefs = Prefs(ctx)
        val rm = RestrictionsManager(ctx)
        val ds = DomainStore(ctx)

        val restrictions = JSONObject()
        RestrictionsManager.all().forEach { restrictions.put(it.id, rm.isApplied(it)) }

        val firewall = JSONObject()
            .put("enabled", prefs.firewallEnabled)
            .put("blockWholeDevice", prefs.blockWholeDevice)
            .put("filterMode", prefs.filterMode)

        val kiosk = JSONObject().put("enabled", prefs.kioskEnabled)

        val domains = JSONObject()
            .put("mode", ds.listMode.name)
            .put("blocked", JSONArray(ds.blocked().toList()))
            .put("allowed", JSONArray(ds.allowed().toList()))

        val mf = MapsFilter(ctx)
        val maps = JSONObject()
            .put("enabled", mf.enabled)
            .put("extra", JSONArray(mf.extra().toList()))

        return JSONObject()
            .put("restrictions", restrictions)
            .put("firewall", firewall)
            .put("kiosk", kiosk)
            .put("domains", domains)
            .put("mapsFilter", maps)
            .put("watermark", JSONObject().put("enabled", com.nativ.filter.watermark.WatermarkController.enabled(ctx)))
    }

    /** מחיל מדיניות שהתקבלה מהשרת על המכשיר. */
    fun apply(ctx: Context, policy: JSONObject) {
        val rm = RestrictionsManager(ctx)
        val fw = FirewallController(ctx)
        val kiosk = KioskController(ctx)
        val ds = DomainStore(ctx)

        // הגבלות
        policy.optJSONObject("restrictions")?.let { r ->
            if (rm.isDeviceOwner()) {
                r.keys().forEach { id ->
                    val def = rm.byId(id) ?: return@forEach
                    val want = r.optBoolean(id, false)
                    if (rm.isApplied(def) != want) rm.apply(def, want)
                }
            }
        }

        // חומת אש
        policy.optJSONObject("firewall")?.let { f ->
            fw.setFilterMode(f.optBoolean("filterMode", fw.filterMode()))
            fw.setBlockWholeDevice(f.optBoolean("blockWholeDevice", fw.blockWholeDevice()))
            val wantOn = f.optBoolean("enabled", fw.isRunning())
            if (wantOn && !fw.isRunning()) fw.enable()
            else if (!wantOn && fw.isRunning()) fw.disable()
        }

        // קיוסק
        policy.optJSONObject("kiosk")?.let { k ->
            val want = k.optBoolean("enabled", kiosk.isEnabled())
            if (want && !kiosk.isEnabled()) kiosk.enable()
            else if (!want && kiosk.isEnabled()) kiosk.disable()
        }

        // דומיינים
        policy.optJSONObject("domains")?.let { d ->
            runCatching { ds.listMode = DomainStore.ListMode.valueOf(d.optString("mode", "BLOCK")) }
            ds.setBlockedAll(toList(d.optJSONArray("blocked")))
            ds.setAllowedAll(toList(d.optJSONArray("allowed")))
        }

        // סמל כשרות על המסך
        policy.optJSONObject("watermark")?.let { w ->
            val wc = com.nativ.filter.watermark.WatermarkController
            wc.setEnabled(ctx, w.optBoolean("enabled", wc.enabled(ctx)))
        }

        // סינון גוגל מפות
        policy.optJSONObject("mapsFilter")?.let { m ->
            val mf = MapsFilter(ctx)
            mf.enabled = m.optBoolean("enabled", mf.enabled)
            mf.setExtraAll(toList(m.optJSONArray("extra")))
        }

        EventLog.info(ctx, "Sync", "מדיניות מהשרת הוחלה")
    }

    fun deviceStatus(ctx: Context): JSONObject {
        val fw = FirewallController(ctx)
        val rm = RestrictionsManager(ctx)
        return JSONObject()
            .put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("android", Build.VERSION.RELEASE)
            .put("deviceOwner", rm.isDeviceOwner())
            .put("firewallOn", fw.isRunning())
    }

    private fun toList(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
    }
}
