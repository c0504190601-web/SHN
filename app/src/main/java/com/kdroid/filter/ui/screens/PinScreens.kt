package com.kdroid.filter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kdroid.filter.security.PinManager
import com.kdroid.filter.ui.theme.NeonCyan
import com.kdroid.filter.ui.theme.NeonRed
import com.kdroid.filter.ui.theme.TextMuted

@Composable
fun PinSetupScreen(pm: PinManager, onDone: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("הגדרת קוד מנהל", color = NeonCyan, fontSize = 24.sp)
        Text(
            "בחר קוד בן ${PinManager.MIN_PIN_LEN} ספרות לפחות. הקוד יידרש לכניסה לאזור הניהול.",
            color = TextMuted, fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        OutlinedTextField(
            value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() } },
            label = { Text("קוד חדש") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.padding(vertical = 6.dp)
        )
        OutlinedTextField(
            value = confirm, onValueChange = { confirm = it.filter { c -> c.isDigit() } },
            label = { Text("אימות קוד") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.padding(vertical = 6.dp)
        )
        error?.let { Text(it, color = NeonRed, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Button(
            onClick = {
                when {
                    pin.length < PinManager.MIN_PIN_LEN ->
                        error = "הקוד קצר מדי"
                    pin != confirm ->
                        error = "הקודים אינם תואמים"
                    else -> {
                        if (pm.setPin(pin)) onDone() else error = "שמירת הקוד נכשלה"
                    }
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) { Text("שמור והמשך") }
    }
}

@Composable
fun PinEntryScreen(pm: PinManager, onSuccess: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("כניסת מנהל", color = NeonCyan, fontSize = 24.sp)
        OutlinedTextField(
            value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() } },
            label = { Text("קוד מנהל") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.padding(vertical = 12.dp)
        )
        error?.let { Text(it, color = NeonRed, fontSize = 13.sp) }
        Button(
            onClick = {
                when (val r = pm.verify(pin)) {
                    is PinManager.Result.Success -> onSuccess()
                    is PinManager.Result.Wrong ->
                        error = "קוד שגוי (ניסיון ${r.attempts})"
                    is PinManager.Result.Locked ->
                        error = "יותר מדי ניסיונות. נסה שוב בעוד ${r.remainingMs / 1000} שניות"
                    is PinManager.Result.NotSet ->
                        error = "לא הוגדר קוד"
                }
                pin = ""
            },
            modifier = Modifier.padding(top = 12.dp)
        ) { Text("כניסה") }
    }
}
