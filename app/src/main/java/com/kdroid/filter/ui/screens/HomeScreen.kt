package com.kdroid.filter.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
import com.kdroid.filter.ui.theme.NeonCyan
import com.kdroid.filter.ui.theme.TextMuted
import com.kdroid.filter.ui.theme.TextPrimary

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val owner = remember { DeviceOwnerManager(ctx) }
    val diag = remember { Diagnostics(ctx) }

    var refreshKey by remember { mutableStateOf(0) }
    val reports = remember(refreshKey) { diag.run() }
    val info = remember(refreshKey) { diag.deviceInfo() }
    var activationMsg by remember { mutableStateOf<String?>(null) }
    var iconHidden by remember { mutableStateOf(IconVisibility.isHidden(ctx)) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("K-Droid Filter — ניהול", color = NeonCyan, fontSize = 22.sp)
        Spacer(Modifier.width(4.dp))

        // --- מצב הגנה כללי ---
        KCard("מצב הגנה") {
            val doOn = reports.firstOrNull { it.key == "device_owner" }?.status == CapabilityStatus.SUPPORTED
            val pinOn = reports.firstOrNull { it.key == "pin" }?.status == CapabilityStatus.SUPPORTED
            Text(
                if (doOn) "המכשיר מנוהל כ-Device Owner." else "המכשיר עדיין אינו Device Owner — חלק מההגבלות לא יחולו.",
                color = TextPrimary, fontSize = 14.sp
            )
            Text(
                "קוד מנהל: " + if (pinOn) "מוגדר" else "לא מוגדר",
                color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }

        // --- פרטי מכשיר ---
        KCard("פרטי מכשיר") {
            info.forEach { (k, v) -> KeyValueRow(k, v) }
        }

        // --- אשף Device Owner ---
        KCard("הפעלת Device Owner") {
            Text("אפשרות 1 — ADB (מכשיר מחובר למחשב):", color = TextPrimary, fontSize = 14.sp)
            Text(
                owner.adbCommand,
                color = NeonCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Row {
                OutlinedButton(onClick = { copy(ctx, owner.adbCommand) }) { Text("העתק פקודה") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { refreshKey++ }) { Text("בדוק מצב") }
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "אפשרות 2 — Root:" + if (RootUtil.isSuPresent()) "" else " (לא זוהה Root)",
                color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)
            )
            Button(
                onClick = {
                    val r = owner.activateViaRoot()
                    activationMsg = r.message
                    refreshKey++
                },
                enabled = RootUtil.isSuPresent()
            ) { Text("הפעל דרך Root") }
            activationMsg?.let {
                Text(it, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        // --- אבחון יכולות ---
        KCard("אבחון יכולות") {
            reports.forEach { rep ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(rep.title, color = TextPrimary, fontSize = 14.sp)
                        if (rep.detail.isNotBlank())
                            Text(rep.detail, color = TextMuted, fontSize = 12.sp)
                    }
                    StatusChip(rep.status)
                }
            }
            OutlinedButton(onClick = { refreshKey++ }, modifier = Modifier.padding(top = 8.dp)) {
                Text("רענן אבחון")
            }
        }

        // --- הסתרת אייקון ---
        KCard("הסתרת אייקון") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("הסתר אייקון ממגירת האפליקציות", color = TextPrimary, fontSize = 14.sp)
                    Text(
                        "כניסה כשמוסתר: חיוג *#*#5374#*#* . גיבוי: ADB am start.",
                        color = TextMuted, fontSize = 12.sp
                    )
                }
                Switch(
                    checked = iconHidden,
                    onCheckedChange = {
                        IconVisibility.setHidden(ctx, it)
                        iconHidden = it
                    }
                )
            }
        }

        // --- יומן ---
        KCard("יומן אירועים (אחרונים)") {
            val lines = remember(refreshKey) { EventLog.read(ctx, 20) }
            if (lines.isEmpty()) Text("אין רשומות עדיין.", color = TextMuted, fontSize = 13.sp)
            else lines.forEach {
                Text(it, color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 1.dp))
            }
        }
    }
}

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("adb", text))
}
