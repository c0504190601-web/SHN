package com.kosherride.filter.ui.screens

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.kosherride.filter.devadmin.DeviceOwnerManager
import com.kosherride.filter.diagnostics.Diagnostics
import com.kosherride.filter.icon.IconVisibility
import com.kosherride.filter.log.EventLog
import com.kosherride.filter.model.CapabilityStatus
import com.kosherride.filter.root.RootUtil
import com.kosherride.filter.ui.KCard
import com.kosherride.filter.ui.KeyValueRow
import com.kosherride.filter.ui.StatusChip
import com.kosherride.filter.ui.theme.NeonAmber
import com.kosherride.filter.ui.theme.NeonCyan
import com.kosherride.filter.ui.theme.NeonGreen
import com.kosherride.filter.ui.theme.NeonRed
import androidx.compose.ui.graphics.Color
import com.kosherride.filter.ui.theme.TextMuted
import com.kosherride.filter.ui.theme.TextPrimary

private val TABS = listOf("לוח בקרה", "אפליקציות", "חומת אש", "סינון", "אבחון", "הגבלות", "יומן")

@Composable
fun HomeScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // אתחול חד-פעמי של רשימת האפליקציות ה"ידועות" — לזיהוי חדשות בהמשך.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val store = com.kosherride.filter.apps.AppPolicyStore(ctx)
            store.seedKnownIfEmpty(com.kosherride.filter.apps.AppRepository(ctx).allPackageNames())
        }
    }
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("נסיעה כשרה", color = NeonCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("· ניהול", color = TextMuted, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            val num = deviceNumberState()
            if (num != null) Text("מכשיר מס' $num", color = NeonCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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

    if (com.kosherride.filter.kiosk.KioskActivity.maintenance) {
        KCard {
            Text("מצב תחזוקה פעיל — הקיוסק מושהה.", color = NeonAmber, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            OutlinedButton(
                onClick = {
                    com.kosherride.filter.kiosk.KioskActivity.maintenance = false
                    (ctx as? android.app.Activity)?.finish()
                },
                modifier = Modifier.padding(top = 6.dp)
            ) { Text("סיום תחזוקה וחזרה לקיוסק") }
        }
    }

    ServerCard(refreshKey = refresh, onChanged = { refresh++ })

    WatermarkCard()

    KCard("מצב הגנה") {
        Text(
            if (isDO) "המכשיר מנוהל כ-Device Owner." else "המכשיר אינו Device Owner — חלק מההגבלות לא יחולו.",
            color = if (isDO) NeonGreen else NeonAmber, fontSize = 15.sp, fontWeight = FontWeight.Medium
        )
        Text("קוד מנהל: מוגדר", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }

    KCard("פרטי מכשיר") { info.forEach { (k, v) -> KeyValueRow(k, v) } }

    ReleaseCard(onDone = { refresh++ })

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
                OutlinedButton(onClick = { refresh++; com.kosherride.filter.sync.SyncScheduler.now(ctx) }) { Text("בדוק מצב") }
            }
            if (RootUtil.isSuPresent()) {
                OutlinedButton(
                    onClick = { activationMsg = owner.activateViaRoot().message; refresh++; com.kosherride.filter.sync.SyncScheduler.now(ctx) },
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

/** הסרת ניהול: מחזיר את המכשיר למצב רגיל ומסיר את Device Owner. */
@Composable
private fun ReleaseCard(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val isDO = remember { DeviceOwnerManager(ctx).isDeviceOwner() }
    var ask by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    if (!isDO && result == null) return

    KCard("הסרת ניהול") {
        Text(
            "מבטל את כל ההגבלות, החסימות, הקיוסק, חומת האש וסמל הכשרות, מחזיר אפליקציות מוסתרות, " +
                "ומסיר את האפליקציה מניהול המכשיר. אחרי זה אפשר להסיר אותה כמו כל אפליקציה.",
            color = TextMuted, fontSize = 12.sp
        )
        androidx.compose.material3.Button(
            onClick = { ask = true },
            enabled = isDO,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = NeonRed),
            modifier = Modifier.padding(top = 8.dp)
        ) { Text("הסר ניהול והחזר את המכשיר למצב רגיל", color = Color.White) }
        result?.let { Text(it, color = NeonAmber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
    }

    if (ask) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("להסיר את הניהול?") },
            text = { Text("כל ההגבלות יבוטלו והמכשיר יחזור למצב רגיל. לחזרה לניהול צריך להגדיר שוב Device Owner דרך מחשב.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    ask = false
                    result = com.kosherride.filter.devadmin.ReleaseManager.release(ctx).message
                    onDone()
                }) { Text("כן, הסר ניהול", color = NeonRed) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { ask = false }) { Text("ביטול") }
            }
        )
    }
}

/** סמל כשרות קבוע במרכז המסך. */
@Composable
private fun WatermarkCard() {
    val ctx = LocalContext.current
    val wc = com.kosherride.filter.watermark.WatermarkController
    var refresh by remember { mutableStateOf(0) }
    val on = remember(refresh) { wc.enabled(ctx) }
    // ההרשאה ניתנת במסך ההגדרות — בודקים שוב כשחוזרים לאפליקציה
    var perm by remember { mutableStateOf(wc.hasPermission(ctx)) }
    androidx.compose.runtime.LaunchedEffect(on) {
        while (on && !perm) {
            kotlinx.coroutines.delay(1500)
            perm = wc.hasPermission(ctx)
            if (perm) wc.sync(ctx)
        }
    }

    KCard("סמל כשרות על המסך") {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("הצג סמל קבוע במרכז המסך", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    when { !on -> "כבוי"; perm -> "מוצג"; else -> "ממתין להרשאה" },
                    color = when { !on -> TextMuted; perm -> NeonGreen; else -> NeonAmber }, fontSize = 12.sp
                )
            }
            androidx.compose.material3.Switch(
                checked = on,
                onCheckedChange = { wc.setEnabled(ctx, it); refresh++ }
            )
        }
        Text(
            "סמל מגן חצי-שקוף עם הכיתוב \"נסיעה כשרה\". לחיצות עוברות דרכו, כך שהוא לא מפריע לשימוש.",
            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )
        // מיקום הסמל
        var pos by remember { mutableStateOf(wc.position(ctx)) }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("מיקום:", color = TextPrimary, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            ModeChip("במרכז (גדול)", pos == "center") { wc.setPosition(ctx, "center"); pos = "center" }
            Spacer(Modifier.width(6.dp))
            ModeChip("בצד שמאל (קטן)", pos == "left") { wc.setPosition(ctx, "left"); pos = "left" }
        }
        if (on && !perm) {
            Text(
                "נדרשת הרשאה חד-פעמית: \"הצגה מעל אפליקציות אחרות\".",
                color = NeonAmber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
            )
            Row(Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = { runCatching { ctx.startActivity(wc.permissionIntent(ctx)) } }) { Text("פתח הרשאה") }
                if (RootUtil.isSuPresent()) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        if (wc.grantViaRoot(ctx)) { perm = wc.hasPermission(ctx); wc.sync(ctx) }
                    }) { Text("אשר דרך Root") }
                }
            }
            Text(
                "או דרך ADB: adb shell appops set com.kosherride.filter SYSTEM_ALERT_WINDOW allow",
                color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** מספר המכשיר מהשרת; מתעדכן לבד כשהרישום מסתיים. */
@Composable
private fun deviceNumberState(): String? {
    val ctx = LocalContext.current
    val prefs = remember { com.kosherride.filter.security.Prefs(ctx) }
    var num by remember { mutableStateOf(prefs.deviceId?.takeIf { prefs.isEnrolled }) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (num == null) {
            kotlinx.coroutines.delay(3000)
            num = prefs.deviceId?.takeIf { prefs.isEnrolled }
        }
    }
    return num
}

@Composable
private fun ServerCard(refreshKey: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { com.kosherride.filter.security.Prefs(ctx) }
    val owner = remember { DeviceOwnerManager(ctx) }
    val scope = rememberCoroutineScope()
    val num = deviceNumberState()
    val isDO = remember(refreshKey) { owner.isDeviceOwner() }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    KCard("מספר מכשיר ושליטה מרחוק") {
        when {
            num != null -> {
                Text(num, color = NeonCyan, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                Text(
                    "זה מספר המכשיר באתר הניהול. כדי לשנות הגדרות מרחוק — נכנסים באתר למכשיר לפי המספר הזה.",
                    color = TextMuted, fontSize = 12.sp
                )
            }
            !isDO -> Text(
                "המספר יינתן אוטומטית מיד אחרי הפעלת הרשאת הניהול (Device Owner).",
                color = NeonAmber, fontSize = 13.sp
            )
            else -> {
                val online = remember(refreshKey, msg) {
                    val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    cm.getNetworkCapabilities(cm.activeNetwork)
                        ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                }
                if (online) {
                    Text("מתחבר לשרת ומקבל מספר מכשיר…", color = NeonAmber, fontSize = 13.sp)
                    Text("השרת יכול לקחת עד דקה להתעורר.", color = TextMuted, fontSize = 12.sp)
                } else {
                    Text("ממתין לחיבור לאינטרנט", color = NeonAmber, fontSize = 13.sp)
                    Text("אין צורך לעשות דבר — בפעם הראשונה שיהיה אינטרנט המכשיר יקבל מספר לבד.", color = TextMuted, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { com.kosherride.filter.sync.SyncScheduler.now(ctx); msg = "מנסה שוב…" },
                    modifier = Modifier.padding(top = 6.dp)
                ) { Text("נסה שוב") }
            }
        }
        if (num != null) {
            val lastSync = if (prefs.lastSyncAt > 0)
                java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(prefs.lastSyncAt))
            else "—"
            KeyValueRow("סנכרון אחרון", lastSync)
            Row(Modifier.padding(top = 6.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true; msg = "מסנכרן..."
                    scope.launch {
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.kosherride.filter.sync.SyncClient(ctx).sync()
                        }
                        msg = if (ok) "סונכרן" else "הסנכרון נכשל"; busy = false; onChanged()
                    }
                }) { Text("סנכרן עכשיו") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true; msg = "שולח..."
                    scope.launch {
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.kosherride.filter.sync.SyncClient(ctx).pushLocal()
                        }
                        msg = if (ok) "המצב נשלח לאתר" else "השליחה נכשלה"; busy = false
                    }
                }) { Text("שלח מצב נוכחי לאתר") }
            }
        }
        msg?.let { Text(it, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun FirewallTab() {
    val ctx = LocalContext.current
    val fw = remember { com.kosherride.filter.firewall.FirewallController(ctx) }
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

    KCard("ברירת מחדל לאפליקציות") {
        val store = remember { com.kosherride.filter.apps.AppPolicyStore(ctx) }
        var defAllow by remember { mutableStateOf(store.defaultAllow) }
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (defAllow) "הכול מותר, חוץ ממה שחסמתי" else "הכול חסום, חוץ ממה שאישרתי",
                    color = TextPrimary, fontSize = 14.sp
                )
                Text(
                    if (defAllow) "חוסמים אפליקציה בלשונית 'אפליקציות' ← כיבוי 'אפשר אינטרנט'. אפליקציות חדשות ממתינות לאישור."
                    else "רק אפליקציות שהופעל להן 'אפשר אינטרנט' גולשות.",
                    color = TextMuted, fontSize = 12.sp
                )
            }
            androidx.compose.material3.Switch(
                checked = defAllow,
                onCheckedChange = {
                    store.defaultAllow = it; defAllow = it
                    fw.rebuild(); refresh++
                }
            )
        }
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
    val store = remember { com.kosherride.filter.filter.DomainStore(ctx) }
    var refresh by remember { mutableStateOf(0) }
    val mode = remember(refresh) { store.listMode }

    KCard("מצב סינון") {
        Text(
            "פעיל רק כאשר חומת האש במצב 'סינון דומיינים'.",
            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)
        )
        Row {
            ModeChip("רשימה שחורה (הכול מותר חוץ מ-)", mode == com.kosherride.filter.filter.DomainStore.ListMode.BLOCK) {
                store.listMode = com.kosherride.filter.filter.DomainStore.ListMode.BLOCK; refresh++
            }
            Spacer(Modifier.width(6.dp))
            ModeChip("רשימה לבנה (הכול חסום חוץ מ-)", mode == com.kosherride.filter.filter.DomainStore.ListMode.ALLOW) {
                store.listMode = com.kosherride.filter.filter.DomainStore.ListMode.ALLOW; refresh++
            }
        }
        Text("שרת DNS במעלה הזרם: ${store.upstreamDns}", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }

    MapsFilterCard()

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

/** סינון תמונות וסרטונים בגוגל מפות. */
@Composable
private fun MapsFilterCard() {
    val ctx = LocalContext.current
    val maps = remember { com.kosherride.filter.filter.MapsFilter(ctx) }
    val fw = remember { com.kosherride.filter.firewall.FirewallController(ctx) }
    var refresh by remember { mutableStateOf(0) }
    val on = remember(refresh) { maps.enabled }
    val ready = remember(refresh) { fw.isRunning() && fw.filterMode() }
    val installed = remember {
        com.kosherride.filter.filter.MapsFilter.PACKAGES.any {
            runCatching { ctx.packageManager.getApplicationInfo(it, 0) }.isSuccess
        }
    }

    KCard("סינון גוגל מפות") {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("חסימת תמונות וסרטונים", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        !on -> "כבוי"
                        ready -> "פעיל — תמונות, סרטונים ו-Street View חסומים"
                        else -> "מופעל, אך ממתין למצב 'סינון דומיינים'"
                    },
                    color = when { !on -> TextMuted; ready -> NeonGreen; else -> NeonAmber }, fontSize = 12.sp
                )
            }
            androidx.compose.material3.Switch(
                checked = on,
                onCheckedChange = {
                    maps.enabled = it
                    EventLog.info(ctx, "Maps", if (it) "סינון גוגל מפות הופעל" else "סינון גוגל מפות כובה")
                    refresh++
                }
            )
        }
        Text(
            "המפה, החיפוש והניווט ממשיכים לעבוד. ללא Root. " +
                (if (android.os.Build.VERSION.SDK_INT >= 29) "חל על גוגל מפות בלבד."
                 else "באנדרואיד מתחת ל-10 החסימה חלה על כל המכשיר (גם תמונות Google באפליקציות אחרות)."),
            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )
        if (!installed) Text("גוגל מפות לא מותקנת במכשיר.", color = NeonAmber, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        if (on && !ready) {
            Text(
                "הסינון עובד דרך חומת האש במצב 'סינון דומיינים'. במצב 'חסימה לפי אפליקציה' גוגל מפות עוקפת את המנהרה.",
                color = NeonAmber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
            )
            OutlinedButton(
                onClick = {
                    fw.setFilterMode(true)
                    if (!fw.isRunning()) {
                        if (fw.consentIntent() == null) fw.enable()
                        else EventLog.warn(ctx, "Maps", "נדרש אישור VPN — הפעל את חומת האש בלשונית 'חומת אש'")
                    }
                    refresh++
                },
                modifier = Modifier.padding(top = 4.dp)
            ) { Text("עבור למצב סינון דומיינים והפעל") }
        }
        if (on) Text(
            "אחרי ההפעלה: עצור את גוגל מפות ונקה את המטמון שלה, כדי שתמונות שכבר נשמרו לא יוצגו.",
            color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)
        )
    }

    DomainEditor(
        title = "גוגל מפות — כתובות נוספות לחסימה",
        items = remember(refresh) { maps.extra().toList() },
        onAdd = { maps.addExtra(it); refresh++ },
        onRemove = { maps.removeExtra(it); refresh++ }
    )
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
    val mgr = remember { com.kosherride.filter.restrictions.RestrictionsManager(ctx) }
    var refresh by remember { mutableStateOf(0) }
    val isDO = remember(refresh) { mgr.isDeviceOwner() }
    val groups = remember { com.kosherride.filter.restrictions.RestrictionsManager.all().groupBy { it.group } }

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
                // לחיצה על כל השורה מחליפה את המתג
                val toggle = { on: Boolean ->
                    val r = mgr.apply(def, on)
                    if (!r.ok) msg = r.message
                    refresh++
                }
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = isDO) { toggle(!applied) }
                        .padding(vertical = 2.dp),
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
                        onCheckedChange = { on -> toggle(on) }
                    )
                }
            }
        }
    }

    KCard("קיוסק ו-Launcher") {
        val kiosk = remember { com.kosherride.filter.kiosk.KioskController(ctx) }
        var kmsg by remember(refresh) { mutableStateOf<String?>(null) }
        val enabled = remember(refresh) { kiosk.isEnabled() }
        Row(
            Modifier.fillMaxWidth().clickable {
                kmsg = if (!enabled) kiosk.enable() else kiosk.disable(); refresh++
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("מצב קיוסק", color = TextPrimary, fontSize = 14.sp)
                Text(
                    if (enabled) "פעיל — המכשיר נפתח למסך הקיוסק" else "כבוי — מסך בית רגיל",
                    color = TextMuted, fontSize = 12.sp
                )
            }
            androidx.compose.material3.Switch(
                checked = enabled,
                onCheckedChange = { kmsg = if (it) kiosk.enable() else kiosk.disable(); refresh++ }
            )
        }
        Text(
            "בחר אילו אפליקציות יופיעו במסך הקיוסק בלשונית 'אפליקציות' (הצג במסך הבית).",
            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )
        OutlinedButton(onClick = { kiosk.openKiosk() }, modifier = Modifier.padding(top = 6.dp)) {
            Text("פתח קיוסק עכשיו")
        }
        Text(
            "יציאה: כפתור 'תחזוקה' במסך הקיוסק + קוד מנהל. הפעלה אוטומטית לאחר אתחול.",
            color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)
        )
        kmsg?.let { Text(it, color = NeonAmber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
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
