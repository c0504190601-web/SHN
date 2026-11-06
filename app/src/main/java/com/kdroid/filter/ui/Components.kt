package com.kdroid.filter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kdroid.filter.model.CapabilityStatus
import com.kdroid.filter.ui.theme.NeonAmber
import com.kdroid.filter.ui.theme.NeonCyan
import com.kdroid.filter.ui.theme.NeonGreen
import com.kdroid.filter.ui.theme.NeonRed
import com.kdroid.filter.ui.theme.SurfaceDark
import com.kdroid.filter.ui.theme.SurfaceElevated
import com.kdroid.filter.ui.theme.TextMuted
import com.kdroid.filter.ui.theme.TextPrimary

@Composable
fun KCard(title: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(SurfaceDark, RoundedCornerShape(16.dp))
            .border(1.dp, SurfaceElevated, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        if (title != null) {
            Text(title, color = NeonCyan, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.padding(top = 10.dp)) { content() }
        } else content()
    }
}

fun statusColor(s: CapabilityStatus): Color = when (s) {
    CapabilityStatus.SUPPORTED -> NeonGreen
    CapabilityStatus.FAILED -> NeonRed
    CapabilityStatus.NEEDS_DEVICE_OWNER -> NeonAmber
    CapabilityStatus.NEEDS_ROOT -> NeonAmber
    CapabilityStatus.NEEDS_PERMISSION -> NeonAmber
    CapabilityStatus.UNSUPPORTED -> NeonRed
    CapabilityStatus.UNKNOWN -> TextMuted
}

fun statusLabel(s: CapabilityStatus): String = when (s) {
    CapabilityStatus.SUPPORTED -> "פועל"
    CapabilityStatus.FAILED -> "נכשל"
    CapabilityStatus.NEEDS_DEVICE_OWNER -> "נדרש Device Owner"
    CapabilityStatus.NEEDS_ROOT -> "נדרש Root"
    CapabilityStatus.NEEDS_PERMISSION -> "נדרשת הרשאה"
    CapabilityStatus.UNSUPPORTED -> "לא נתמך"
    CapabilityStatus.UNKNOWN -> "דורש בדיקה"
}

@Composable
fun StatusChip(status: CapabilityStatus) {
    val c = statusColor(status)
    Box(
        Modifier
            .background(c.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(statusLabel(status), color = c, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
