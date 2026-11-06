package com.kdroid.filter.sync

import android.content.Context
import android.os.Build
import com.kdroid.filter.filter.DomainStore
import com.kdroid.filter.firewall.FirewallController
import com.kdroid.filter.kiosk.KioskController
import com.kdroid.filter.log.EventLog
import com.kdroid.filter.restrictions.RestrictionsManager
import com.kdroid.filter.security.Prefs
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

        return JSONObject()
            .put("restrictions", restrictions)
            .put("firewall", firewall)
            .put("kiosk", kiosk)
            .put("domains", domains)
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
