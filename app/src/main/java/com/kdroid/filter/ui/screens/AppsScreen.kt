package com.kdroid.filter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kdroid.filter.apps.AppManager
import com.kdroid.filter.apps.AppRepository
import com.kdroid.filter.apps.IconLoader
import com.kdroid.filter.model.AppPolicyState
import com.kdroid.filter.ui.theme.NeonAmber
import com.kdroid.filter.ui.theme.NeonCyan
import com.kdroid.filter.ui.theme.NeonGreen
import com.kdroid.filter.ui.theme.NeonRed
import com.kdroid.filter.ui.theme.SurfaceDark
import com.kdroid.filter.ui.theme.SurfaceElevated
import com.kdroid.filter.ui.theme.TextMuted
import com.kdroid.filter.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Filter(val label: String) {
    ALL("הכל"), MANAGED("מנוהלות"), HOME("במסך הבית"), PENDING("ממתינות לאישור")
}

@Composable
fun AppsScreen() {
    val ctx = LocalContext.current
    val repo = remember { AppRepository(ctx) }
    val mgr = remember { AppManager(ctx) }

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var includeSystem by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    // טעינת רשימה ברקע
    val apps by produceState(initialValue = emptyList<AppPolicyState>(), reload) {
        value = withContext(Dispatchers.IO) {
            repo.installedApps().map { mgr.stateOf(it) }
        }
    }

    val filtered = apps.filter { st ->
        (includeSystem || !st.info.isSystem) &&
            (query.isBlank() ||
                st.info.label.contains(query, true) ||
                st.info.packageName.contains(query, true)) &&
            when (filter) {
                Filter.ALL -> true
                Filter.MANAGED -> st.hidden || st.suspended || st.uninstallBlocked || !st.netAllowed || st.inAppBrowserBlocked
                Filter.HOME -> st.onHome
                Filter.PENDING -> st.pendingApproval
            }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("חיפוש לפי שם או חבילה") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Filter.entries.forEach { f ->
                Chip(f.label, filter == f) { filter = f }
            }
            Chip(if (includeSystem) "כולל מערכת ✓" else "אפליקציות מערכת", includeSystem) {
                includeSystem = !includeSystem
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${filtered.size} אפליקציות · הקש על שורה לפעולות",
            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)
        )

        if (!mgr.isDeviceOwner()) {
            Text(
                "שים לב: ללא Device Owner פעיל, פעולות הסתרה/השעיה/מניעת הסרה לא יחולו.",
                color = NeonAmber, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(filtered, key = { it.info.packageName }) { st ->
                AppRow(st, mgr) { reload++ }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = if (selected) NeonCyan else TextMuted
    Box(
        Modifier
            .padding(vertical = 2.dp)
            .background(
                if (selected) NeonCyan.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(20.dp)
            )
            .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(label, color = c, fontSize = 12.sp) }
}

@Composable
private fun AppRow(st: AppPolicyState, mgr: AppManager, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, st.info.packageName) {
        value = withContext(Dispatchers.IO) { IconLoader.load(ctx, st.info.packageName) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(SurfaceDark, RoundedCornerShape(14.dp))
            .border(1.dp, SurfaceElevated, RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(bitmap = icon!!, contentDescription = null, modifier = Modifier.size(38.dp))
            } else {
                Box(Modifier.size(38.dp).background(SurfaceElevated, RoundedCornerShape(10.dp)))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(st.info.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(st.info.packageName, color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        // שבבי מצב
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 8.dp)) {
            if (st.pendingApproval) MiniChip("ממתינה לאישור", NeonAmber)
            MiniChip(if (st.netAllowed) "רשת: מותר" else "רשת: חסום", if (st.netAllowed) NeonGreen else NeonRed)
            if (st.hidden) MiniChip("מוסתרת", NeonAmber)
            if (st.suspended) MiniChip("מושעית", NeonAmber)
            if (st.uninstallBlocked) MiniChip("חסומה להסרה", NeonCyan)
            if (st.inAppBrowserBlocked) MiniChip("דפדפן פנימי חסום", NeonPurpleSafe)
            if (st.onHome) MiniChip("במסך הבית", NeonCyan)
        }

        if (expanded) {
            Spacer(Modifier.height(6.dp))
            ToggleRow("אפשר אינטרנט", st.netAllowed) { mgr.setNetAllowed(st.info.packageName, it); onChanged() }
            ToggleRow("חסום דפדפן פנימי", st.inAppBrowserBlocked) { mgr.setInAppBrowserBlocked(st.info.packageName, it); onChanged() }
            ToggleRow("הצג במסך הבית (קיוסק)", st.onHome) { mgr.setOnHome(st.info.packageName, it); onChanged() }
            ToggleRow("הסתר אפליקציה", st.hidden) { msg = mgr.setHidden(st.info.packageName, it).message; onChanged() }
            ToggleRow("השעה אפליקציה", st.suspended) { msg = mgr.setSuspended(st.info.packageName, it).message; onChanged() }
            ToggleRow("מנע הסרה", st.uninstallBlocked) { msg = mgr.setUninstallBlocked(st.info.packageName, it).message; onChanged() }
            if (st.pendingApproval) {
                Text(
                    "אפליקציה חדשה — חסומה לרשת עד אישור. הפעלת 'אפשר אינטרנט' מאשרת אותה.",
                    color = NeonAmber, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
                )
            }
            msg?.let { Text(it, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

private val NeonPurpleSafe = com.kdroid.filter.ui.theme.NeonPurple

@Composable
private fun MiniChip(text: String, c: Color) {
    Box(
        Modifier
            .background(c.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) { Text(text, color = c, fontSize = 10.sp) }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
