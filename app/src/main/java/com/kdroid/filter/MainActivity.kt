package com.kdroid.filter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.kdroid.filter.devadmin.DeviceOwnerManager
import com.kdroid.filter.security.PinManager
import com.kdroid.filter.security.Prefs
import com.kdroid.filter.ui.screens.HomeScreen
import com.kdroid.filter.ui.screens.OnboardingScreen
import com.kdroid.filter.ui.screens.PinEntryScreen
import com.kdroid.filter.ui.screens.PinSetupScreen
import com.kdroid.filter.ui.theme.KDroidTheme

class MainActivity : ComponentActivity() {

    private enum class Phase { ONBOARD, SETUP_PIN, ENTER_PIN, HOME }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pm = PinManager(this)
        val prefs = Prefs(this)
        val owner = DeviceOwnerManager(this)

        // סדר פתיחה: קודם Device Owner, ורק אז קוד.
        fun initialPhase(): Phase = when {
            pm.isPinSet -> Phase.ENTER_PIN
            owner.isDeviceOwner() || prefs.setupDone -> Phase.SETUP_PIN
            else -> Phase.ONBOARD
        }

        setContent {
            KDroidTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) {
                        var phase by remember { mutableStateOf(initialPhase()) }
                        when (phase) {
                            Phase.ONBOARD -> OnboardingScreen(
                                onReady = { phase = Phase.SETUP_PIN },
                                onSkipLimited = { prefs.setupDone = true; phase = Phase.SETUP_PIN }
                            )
                            Phase.SETUP_PIN -> PinSetupScreen(pm) {
                                prefs.setupDone = true
                                phase = Phase.HOME
                            }
                            Phase.ENTER_PIN -> PinEntryScreen(pm) { phase = Phase.HOME }
                            Phase.HOME -> HomeScreen()
                        }
                    }
                }
            }
        }
    }
}
