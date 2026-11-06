package com.kdroid.filter.kiosk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kdroid.filter.MainActivity
import com.kdroid.filter.apps.AppPolicyStore
import com.kdroid.filter.apps.AppRepository
import com.kdroid.filter.apps.IconLoader
import com.kdroid.filter.log.EventLog
import com.kdroid.filter.model.AppInfo
import com.kdroid.filter.security.PinManager
import com.kdroid.filter.ui.theme.KDroidTheme
import com.kdroid.filter.ui.theme.NeonCyan
import com.kdroid.filter.ui.theme.SurfaceElevated
import com.kdroid.filter.ui.theme.TextMuted
import com.kdroid.filter.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * מסך הבית של הקיוסק: מציג רק את האפליקציות שהמנהל אישר.
 * נעול ב-Lock Task; יציאה אפשרית רק דרך מצב תחזוקה עם קוד מנהל.
 */
class KioskActivity : ComponentActivity() {

    companion object {
        @Volatile var maintenance: Boolean = false
    }

    private val controller by lazy { KioskController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KDroidTheme {
                Surface(Modifier.fillMaxSize()) { KioskHome(onMaintenance = ::enterMaintenance) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (controller.isEnabled() && controller.isDeviceOwner() && !maintenance) {
            runCatching { startLockTask() }
        }
    }

    private fun enterMaintenance() {
        maintenance = true
        runCatching { stopLockTask() }
        EventLog.warn(this, "Kiosk", "כניסה למצב תחזוקה")
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun KioskHome(onMaintenance: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { AppPolicyStore(ctx) }
    var showPin by remember { mutableStateOf(false) }

    val apps by produceState(initialValue = emptyList<AppInfo>()) {
        value = withContext(Dispatchers.IO) {
            val home = store.homePackages()
            AppRepository(ctx).installedApps().filter { home.contains(it.packageName) }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Box(Modifier.fillMaxSize().padding(bottom = 0.dp)) {
            if (apps.isEmpty()) {
                Text(
                    "לא נבחרו אפליקציות למסך הבית. בחר אותן בלשונית 'אפליקציות'.",
                    color = TextMuted, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(apps, key = { it.packageName }) { app -> KioskIcon(app) }
                }
            }
            // כפתור תחזוקה בפינה
            Text(
                "תחזוקה",
                color = TextMuted, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomStart).clickable { showPin = true }.padding(8.dp)
            )
        }
    }

    if (showPin) {
        MaintenancePinDialog(onDismiss = { showPin = false }, onSuccess = { showPin = false; onMaintenance() })
    }
}

@Composable
private fun KioskIcon(app: AppInfo) {
    val ctx = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) { IconLoader.load(ctx, app.packageName, 144) }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable {
            ctx.packageManager.getLaunchIntentForPackage(app.packageName)?.let { ctx.startActivity(it) }
        }
    ) {
        if (icon != null) {
            Image(bitmap = icon!!, contentDescription = null, modifier = Modifier.size(56.dp))
        } else {
            Box(Modifier.size(56.dp).padding(2.dp))
        }
        Text(app.label, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MaintenancePinDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val ctx = LocalContext.current
    val pm = remember { PinManager(ctx) }
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("מצב תחזוקה", color = NeonCyan) },
        text = {
            Column {
                Text("הקלד קוד מנהל כדי לצאת זמנית מהקיוסק.", color = TextMuted, fontSize = 13.sp)
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.padding(top = 8.dp)
                )
                err?.let { Text(it, color = TextPrimary, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (pm.verify(pin)) {
                    is PinManager.Result.Success -> onSuccess()
                    is PinManager.Result.Locked -> err = "יותר מדי ניסיונות, המתן"
                    else -> { err = "קוד שגוי"; pin = "" }
                }
            }) { Text("אישור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}
