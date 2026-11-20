package com.kosherride.filter.sync

import android.content.Context
import android.os.Build
import com.kosherride.filter.filter.DomainStore
import com.kosherride.filter.filter.MapsFilter
import com.kosherride.filter.firewall.FirewallController
import com.kosherride.filter.kiosk.KioskController
import com.kosherride.filter.log.EventLog
import com.kosherride.filter.restrictions.RestrictionsManager
import com.kosherride.filter.security.Prefs
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

        // אפליקציות: מצב לכל חבילה, בדיוק כמו בלשונית "אפליקציות"
        val apps = JSONObject()
        runCatching {
            val mgr = com.kosherride.filter.apps.AppManager(ctx)
            com.kosherride.filter.apps.AppRepository(ctx).installedApps().forEach { info ->
                val st = mgr.stateOf(info)
                apps.put(info.packageName, JSONObject()
                    .put("net", st.netAllowed)
                    .put("browser", st.inAppBrowserBlocked)
                    .put("home", st.onHome)
                    .put("hidden", st.hidden)
                    .put("suspended", st.suspended)
                    .put("noUninstall", st.uninstallBlocked)
                    .put("pending", st.pendingApproval))
            }
        }

        return JSONObject()
            .put("apps", apps)
            .put("icon", JSONObject().put("hidden", com.kosherride.filter.icon.IconVisibility.isHidden(ctx)))
            .put("restrictions", restrictions)
            .put("firewall", firewall)
            .put("kiosk", kiosk)
            .put("domains", domains)
            .put("mapsFilter", maps)
            .put("watermark", JSONObject().put("enabled", com.kosherride.filter.watermark.WatermarkController.enabled(ctx)))
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

        // אפליקציות
        policy.optJSONObject("apps")?.let { a ->
            val mgr = com.kosherride.filter.apps.AppManager(ctx)
            val store = com.kosherride.filter.apps.AppPolicyStore(ctx)
            var netChanged = false
            var homeChanged = false
            a.keys().forEach { pkg ->
                val o = a.optJSONObject(pkg) ?: return@forEach
                if (o.has("net") && o.optBoolean("net") != store.isNetAllowed(pkg)) {
                    mgr.setNetAllowed(pkg, o.optBoolean("net")); netChanged = true
                }
                if (o.has("browser") && o.optBoolean("browser") != store.isInAppBrowserBlocked(pkg))
                    mgr.setInAppBrowserBlocked(pkg, o.optBoolean("browser"))
                if (o.has("home") && o.optBoolean("home") != store.isOnHome(pkg)) {
                    mgr.setOnHome(pkg, o.optBoolean("home")); homeChanged = true
                }
                if (o.has("hidden") && o.optBoolean("hidden") != mgr.isHidden(pkg)) mgr.setHidden(pkg, o.optBoolean("hidden"))
                if (o.has("suspended") && o.optBoolean("suspended") != mgr.isSuspended(pkg)) mgr.setSuspended(pkg, o.optBoolean("suspended"))
                if (o.has("noUninstall") && o.optBoolean("noUninstall") != mgr.isUninstallBlocked(pkg)) mgr.setUninstallBlocked(pkg, o.optBoolean("noUninstall"))
            }
            if (netChanged) fw.rebuild()
            if (homeChanged && kiosk.isEnabled()) runCatching { kiosk.refreshLockTaskPackages() }
        }

        // אייקון האפליקציה (מוצג / מוסתר) — נשלט מהאתר
        policy.optJSONObject("icon")?.let { i ->
            val want = i.optBoolean("hidden", com.kosherride.filter.icon.IconVisibility.isHidden(ctx))
            if (want != com.kosherride.filter.icon.IconVisibility.isHidden(ctx)) com.kosherride.filter.icon.IconVisibility.setHidden(ctx, want)
        }

        // סמל כשרות על המסך
        policy.optJSONObject("watermark")?.let { w ->
            val wc = com.kosherride.filter.watermark.WatermarkController
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
        val diag = com.kosherride.filter.diagnostics.Diagnostics(ctx)
        val info = JSONArray()
        runCatching { diag.deviceInfo().forEach { (k, v) -> info.put(JSONArray().put(k).put(v)) } }
        val caps = JSONArray()
        runCatching {
            diag.run().forEach { r ->
                caps.put(JSONObject().put("title", r.title).put("status", r.status.name).put("detail", r.detail))
            }
        }
        val counts = runCatching { fw.counts() }.getOrNull()
        // "reported" — תמונת מצב מלאה של המכשיר, כדי שהאתר יציג בדיוק את מה שבאפליקציה
        val reported = JSONObject()
            .put("state", toJson(ctx))
            .put("info", info)
            .put("diag", caps)
            .put("log", JSONArray(EventLog.read(ctx, 150)))
            .put("allowedCount", counts?.allowed ?: 0)
            .put("blockedCount", counts?.blocked ?: 0)
            .put("upstreamDns", runCatching { DomainStore(ctx).upstreamDns }.getOrDefault(""))
            .put("overlayPermission", com.kosherride.filter.watermark.WatermarkController.hasPermission(ctx))
            .put("at", System.currentTimeMillis())
        return JSONObject()
            .put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("android", Build.VERSION.RELEASE)
            .put("deviceOwner", rm.isDeviceOwner())
            .put("firewallOn", fw.isRunning())
            .put("reported", reported)
    }

    /** רשימת האפליקציות עם שמות ואייקונים — נשלחת רק כשהרשימה משתנה. */
    fun appCatalog(ctx: Context): JSONArray {
        val out = JSONArray()
        com.kosherride.filter.apps.AppRepository(ctx).installedApps().take(400).forEach { a ->
            val o = JSONObject().put("p", a.packageName).put("l", a.label).put("s", a.isSystem).put("x", a.launchable)
            // אייקונים רק לאפליקציות שאפשר לפתוח (חוסך נפח)
            if (a.launchable) com.kosherride.filter.apps.IconLoader.pngBase64(ctx, a.packageName)?.let { o.put("i", it) }
            out.put(o)
        }
        return out
    }

    private fun toList(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
    }
}
