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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kdroid.filter.devadmin.DeviceOwnerManager
import com.kdroid.filter.root.RootUtil
import com.kdroid.filter.ui.KCard
import com.kdroid.filter.ui.theme.NeonAmber
import com.kdroid.filter.ui.theme.NeonCyan
import com.kdroid.filter.ui.theme.NeonGreen
import com.kdroid.filter.ui.theme.TextMuted
import com.kdroid.filter.ui.theme.TextPrimary

/**
 * פתיחה ראשונה: קודם הרשאות והפעלת Device Owner.
 * רק כשהמכשיר מזוהה כ-Device Owner עוברים להגדרת קוד.
 */
@Composable
fun OnboardingScreen(onReady: () -> Unit, onSkipLimited: () -> Unit) {
    val ctx = LocalContext.current
    val owner = remember { DeviceOwnerManager(ctx) }
    var refresh by remember { mutableStateOf(0) }
    val isDO = remember(refresh) { owner.isDeviceOwner() }
    var msg by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("ברוך הבא ל-K-Droid Filter", color = NeonCyan, fontSize = 24.sp)
        Text(
            "לפני הגדרת קוד, יש להפוך את האפליקציה למנהל המכשיר (Device Owner). " +
                "כך ההגבלות והסינון יוכלו לפעול. רק לאחר זיהוי הרשאה זו נגדיר קוד מנהל.",
            color = TextMuted, fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        KCard("שלב 1 · הרשאת Device Owner") {
            StepStatus(isDO)
            Spacer(Modifier.height(10.dp))
            Text("אפשרות א' — דרך מחשב (ADB):", color = TextPrimary, fontSize = 14.sp)
            Text(
                owner.adbCommand,
                color = NeonCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Row {
                OutlinedButton(onClick = { copyToClip(ctx, owner.adbCommand) }) { Text("העתק פקודה") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { refresh++ }) { Text("בדוק מצב") }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "אפשרות ב' — דרך Root:" + if (RootUtil.isSuPresent()) "" else " (לא זוהה Root)",
                color = TextPrimary, fontSize = 14.sp
            )
            Button(
                onClick = {
                    val r = owner.activateViaRoot()
                    msg = r.message
                    refresh++
                },
                enabled = RootUtil.isSuPresent(),
                modifier = Modifier.padding(top = 6.dp)
            ) { Text("הפעל דרך Root") }
            msg?.let { Text(it, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        }

        if (isDO) {
            KCard("שלב 2 · הגדרת קוד מנהל") {
                Text("זוהתה הרשאת Device Owner. אפשר להמשיך להגדרת קוד.", color = NeonGreen, fontSize = 14.sp)
                Button(onClick = onReady, modifier = Modifier.padding(top = 10.dp)) {
                    Text("המשך להגדרת קוד")
                }
            }
        } else {
            Text(
                "אם המכשיר אינו תומך ב-Device Owner, אפשר להמשיך במצב מוגבל — חלק מההגבלות לא יחולו.",
                color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
            TextButton(onClick = onSkipLimited) { Text("המשך במצב מוגבל", color = NeonAmber) }
        }
    }
}

@Composable
private fun StepStatus(ok: Boolean) {
    Text(
        if (ok) "● מזוהה כ-Device Owner" else "○ עדיין לא Device Owner",
        color = if (ok) NeonGreen else NeonAmber, fontSize = 14.sp
    )
}

private fun copyToClip(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("adb", text))
}
