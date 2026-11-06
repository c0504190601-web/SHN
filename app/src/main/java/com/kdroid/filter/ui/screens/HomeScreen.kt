package com.kdroid.filter.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kdroid.filter.devadmin.DeviceOwnerManager
import com.kdroid.filter.diagnostics.Diagnostics
import com.kdroid.filter.icon.IconVisibility
import com.kdroid.filter.log.EventLog
import com.kdroid.filter.model.CapabilityStatus
import com.kdroid.filter.root.RootUtil
import com.kdroid.filter.ui.KCard
import com.kdroid.filter.ui.KeyValueRow
import com.kdroid.filter.ui.StatusChip
import com.kdroid.filter.ui.theme.NeonAmber
import com.kdroid.filter.ui.theme.NeonCyan
import com.kdroid.filter.ui.theme.NeonGreen
import com.kdroid.filter.ui.theme.TextMuted
import com.kdroid.filter.ui.theme.TextPrimary

private val TABS = listOf("לוח בקרה", "אפליקציות", "חומת אש", "סינון", "אבחון", "הגבלות", "יומן")

@Composable
fun HomeScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // אתחול חד-פעמי של רשימת האפליקציות ה"ידועות" — לזיהוי חדשות בהמשך.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val store = com.kdroid.filter.apps.AppPolicyStore(ctx)
            store.seedKnownIfEmpty(com.kdroid.filter.apps.AppRepository(ctx).allPackageNames())
        }
    }
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("K-Droid Filter", color = NeonCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("· ניהול", color = TextMuted, fontSize = 14.sp)
        }
        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 12.dp
        ) {
            TABS.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        // לשונית האפליקציות מנהלת גלילה בעצמה (LazyColumn); השאר בגלילה רגילה.
        if (tab == 1) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)
                    .widthIn(max = 720.dp)
            ) { AppsScreen() }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(Modifier.fillMaxWidth().widthIn(max = 720.dp)) {
                    when (tab) {
                        0 -> DashboardTab()
                        2 -> FirewallTab()
                        3 -> FilteringTab()
                        4 -> DiagnosticsTab()
                        5 -> RestrictionsTab()
                        else -> LogTab()
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTab() {
    val ctx = LocalContext.current
    val owner = remember { DeviceOwnerManager(ctx) }
    val diag = remember { Diagnostics(ctx) }
    var refresh by remember { mutableStateOf(0) }
    val isDO = remember(refresh) { owner.isDeviceOwner() }
    val info = remember(refresh) { diag.deviceInfo() }
    var activationMsg by remember { mutableStateOf<String?>(null) }

    KCard("מצב הגנה") {
        Text(
            if (isDO) "המכשיר מנוהל כ-Device Owner." else "המכשיר אינו Device Owner — חלק מההגבלות לא יחולו.",
            color = if (isDO) NeonGreen else NeonAmber, fontSize = 15.sp, fontWeight = FontWeight.Medium
        )
        Text("קוד מנהל: מוגדר", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        Text("חומת אש: תופעל בשלב 3", color = TextMuted, fontSize = 13.sp)
        Text("סנכרון לשרת: יתווסף בשלב 5", color = TextMuted, fontSize = 13.sp)
    }

    KCard("פרטי מכשיר") { info.forEach { (k, v) -> KeyValueRow(k, v) } }

    if (!isDO) {
        KCard("הפעלת Device Owner") {
            Text("אפשרות 1 — ADB:", color = TextPrimary, fontSize = 14.sp)
            Text(
                owner.adbCommand, color = NeonCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Row {
                OutlinedButton(onClick = { copy(ctx, owner.adbCommand) }) { Text("העתק פקודה") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { refresh++ }) { Text("בדוק מצב") }
            }
            if (RootUtil.isSuPresent()) {
                OutlinedButton(
                    onClick = { activationMsg = owner.activateViaRoot().message; refresh++ },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("הפעל דרך Root") }
            }
            activationMsg?.let { Text(it, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun DiagnosticsTab() {
    val ctx = LocalContext.current
    val diag = remember { Diagnostics(ctx) }
    var refresh by remember { mutableStateOf(0) }
    val reports = remember(refresh) { diag.run() }
    var iconHidden by remember { mutableStateOf(IconVisibility.isHidden(ctx)) }

    KCard("אבחון יכולות") {
        Text(
            "מצב אמיתי מה-API. זו דגימה; כל מתגי ההגבלות נבנים בשלב 4.",
            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)
        )
        reports.forEach { rep ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(rep.title, color = TextPrimary, fontSize = 14.sp)
                    if (rep.detail.isNotBlank()) Text(rep.detail, color = TextMuted, fontSize = 12.sp)
                }
                StatusChip(rep.status)
            }
        }
        OutlinedButton(onClick = { refresh++ }, modifier = Modifier.padding(top = 8.dp)) { Text("רענן אבחון") }
    }

    KCard("אייקון האפליקציה") {
        Text(
            "הסתרה והחזרה של האייקון נשלטות מאתר הניהול (שלב 5). " +
                "הכפתור הבא זמני, לבדיקה בלבד, עד שהאתר יוקם.",
            color = TextMuted, fontSize = 12.sp
        )
        Text(
            "מצב נוכחי: " + if (iconHidden) "מוסתר" else "מוצג",
            color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)
        )
        OutlinedButton(
            onClick = { IconVisibility.setHidden(ctx, !iconHidden); iconHidden = !iconHidden },
            modifier = Modifier.padding(top = 6.dp)
        ) { Text(if (iconHidden) "הצג אייקון (זמני)" else "הסתר אייקון (זמני)") }
    }
}

@Composable
private fun FirewallTab() {
    val ctx = LocalContext.current
    val fw = remember { com.kdroid.filter.firewall.FirewallController(ctx) }
    var refresh by remember { mutableStateOf(0) }
    val running = remember(refresh) { fw.isRunning() }
    val blockAll = remember(refresh) { fw.blockWholeDevice() }
    val counts = remember(refresh) { fw.counts() }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) { fw.enable(); refresh++ }
    }

    KCard("חומת אש") {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("הגנת רשת", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (running) "פעילה — אפליקציות לא מורשות חסומות" else "כבויה",
                    color = if (running) NeonGreen else TextMuted, fontSize = 12.sp
                )
            }
            androidx.compose.material3.Switch(
                checked = running,
                onCheckedChange = { on ->
                    if (on) {
                        val intent = fw.consentIntent()
                        if (intent != null) launcher.launch(intent) else { fw.enable(); refresh++ }
                    } else { fw.disable(); refresh++ }
                }
            )
        }
        Text(
            "ברירת המחדל: הכול חסום, ומאשרים לכל אפליקציה בנפרד בלשונית 'אפליקציות'. ללא Root.",
            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )
    }

    KCard("מצב פעולה") {
        val filter = remember(refresh) { fw.filterMode() }
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (filter) "סינון דומיינים (DNS)" else "חסימה לפי אפליקציה", color = TextPrimary, fontSize = 14.sp)
                Text(
                    if (filter) "רשימות אתרים מותרים/חסומים (לשונית 'סינון')"
                    else "כל אפליקציה מותרת או חסומה לרשת במלואה",
                    color = TextMuted, fontSize = 12.sp
                )
            }
            androidx.compose.material3.Switch(
                checked = filter,
                onCheckedChange = { fw.setFilterMode(it); refresh++ }
            )
        }
        Text(
            "אנדרואיד מריץ VPN אחד, לכן בוחרים מצב אחד בכל רגע.",
            color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
        )
    }

    KCard("חסימת אינטרנט לכל המכשיר") {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("נתק אינטרנט לכל האפליקציות (מצב חסימה)", color = TextPrimary, fontSize = 14.sp)
            androidx.compose.material3.Switch(
                checked = blockAll,
                onCheckedChange = { fw.setBlockWholeDevice(it); refresh++ }
            )
        }
    }

    KCard("סיכום") {
        KeyValueRow("מורשות לאינטרנט", counts.allowed.toString())
        KeyValueRow("חסומות", counts.blocked.toString())
        Text(
            "אנדרואיד מאפשר בד\"כ VPN פעיל אחד; VPN אחר עלול להתנגש. " +
                "סינון DNS ורשימות דומיינים (כולל דפדפן פנימי) מגיעים בשלב 3ב.",
            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )
        OutlinedButton(onClick = { refresh++ }, modifier = Modifier.padding(top = 6.dp)) { Text("רענן") }
    }
}

@Composable
private fun FilteringTab() {
    val ctx = LocalContext.current
    val store = remember { com.kdroid.filter.filter.DomainStore(ctx) }
    var refresh by remember { mutableStateOf(0) }
    val mode = remember(refresh) { store.listMode }

    KCard("מצב סינון") {
        Text(
            "פעיל רק כאשר חומת האש במצב 'סינון דומיינים'.",
            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)
        )
        Row {
            ModeChip("רשימה שחורה (הכול מותר חוץ מ-)", mode == com.kdroid.filter.filter.DomainStore.ListMode.BLOCK) {
                store.listMode = com.kdroid.filter.filter.DomainStore.ListMode.BLOCK; refresh++
            }
            Spacer(Modifier.width(6.dp))
            ModeChip("רשימה לבנה (הכול חסום חוץ מ-)", mode == com.kdroid.filter.filter.DomainStore.ListMode.ALLOW) {
                store.listMode = com.kdroid.filter.filter.DomainStore.ListMode.ALLOW; refresh++
            }
        }
        Text("שרת DNS במעלה הזרם: ${store.upstreamDns}", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }

    DomainEditor(
        title = "דומיינים חסומים",
        items = remember(refresh) { store.blocked().toList() },
        onAdd = { store.addBlocked(it); refresh++ },
        onRemove = { store.removeBlocked(it); refresh++ }
    )
    DomainEditor(
        title = "דומיינים מותרים (רשימה לבנה)",
        items = remember(refresh) { store.allowed().toList() },
        onAdd = { store.addAllowed(it); refresh++ },
        onRemove = { store.removeAllowed(it); refresh++ }
    )

    KCard {
        Text(
            "סינון DNS אינו עמיד בפני DoH/DoT או כתובות IP קשיחות. לחסימה קשיחה של אפליקציה " +
                "השתמש במצב 'חסימה לפי אפליקציה'. מומלץ לבדוק על מכשיר אמיתי.",
            color = TextMuted, fontSize = 12.sp
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = if (selected) NeonCyan else TextMuted
    androidx.compose.foundation.layout.Box(
        Modifier
            .border(1.dp, c.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(if (selected) NeonCyan.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent,
                androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) { Text(label, color = c, fontSize = 11.sp) }
}

@Composable
private fun DomainEditor(title: String, items: List<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    KCard(title) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                label = { Text("example.com") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { if (input.isNotBlank()) { onAdd(input); input = "" } }) { Text("הוסף") }
        }
        if (items.isEmpty()) {
            Text("אין פריטים.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
            items.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(d, color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    OutlinedButton(onClick = { onRemove(d) }) { Text("הסר") }
                }
            }
        }
    }
}

@Composable
private fun RestrictionsTab() {
    val ctx = LocalContext.current
    val mgr = remember { com.kdroid.filter.restrictions.RestrictionsManager(ctx) }
    var refresh by remember { mutableStateOf(0) }
    val isDO = remember(refresh) { mgr.isDeviceOwner() }
    val groups = remember { com.kdroid.filter.restrictions.RestrictionsManager.all().groupBy { it.group } }

    if (!isDO) {
        KCard {
            Text(
                "הגבלות המערכת דורשות Device Owner פעיל. הפעל אותו בלשונית 'לוח בקרה'. " +
                    "המתגים מושבתים עד אז.",
                color = NeonAmber, fontSize = 13.sp
            )
        }
    }

    groups.forEach { (group, defs) ->
        KCard(group) {
            defs.forEach { def ->
                var msg by remember(refresh) { mutableStateOf<String?>(null) }
                val applied = remember(refresh) { mgr.isApplied(def) }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(def.title, color = TextPrimary, fontSize = 14.sp)
                        msg?.let { Text(it, color = NeonAmber, fontSize = 11.sp) }
                    }
                    androidx.compose.material3.Switch(
                        checked = applied,
                        enabled = isDO,
                        onCheckedChange = { on ->
                            val r = mgr.apply(def, on)
                            if (!r.ok) msg = r.message
                            refresh++
                        }
                    )
                }
            }
        }
    }

    KCard("קיוסק, אתחול ותחזוקה") {
        Text("Launcher ייעודי, הפעלה לאחר אתחול ומצב תחזוקה — שלב 4ב, בפיתוח.",
            color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun LogTab() {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val lines = remember(refresh) { EventLog.read(ctx, 100) }
    KCard("יומן אירועים ושגיאות") {
        if (lines.isEmpty()) Text("אין רשומות עדיין.", color = TextMuted, fontSize = 13.sp)
        else lines.forEach {
            Text(it, color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 1.dp))
        }
        OutlinedButton(onClick = { refresh++ }, modifier = Modifier.padding(top = 8.dp)) { Text("רענן") }
    }
}

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("adb", text))
}
