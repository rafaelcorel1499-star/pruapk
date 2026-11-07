package com.lapaola.facturas

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lapaola.common.LicenseCore

/** Guarda la activación de este dispositivo. La llave queda ligada al ANDROID_ID del teléfono. */
class LicenseStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("lic", Context.MODE_PRIVATE)

    @SuppressLint("HardwareIds")
    val deviceId: String =
        android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?: "unknown"

    val deviceCode: String get() = LicenseCore.deviceCode(deviceId)

    fun isActivated(): Boolean {
        val k = sp.getString("key", null) ?: return false
        val t = sp.getString("tok", null) ?: return false
        return LicenseCore.verify(k, deviceId) && t == LicenseCore.token(k, deviceId)
    }

    fun activate(input: String): Boolean {
        val k = LicenseCore.normalize(input)
        if (!LicenseCore.verify(k, deviceId)) return false
        sp.edit().putString("key", k).putString("tok", LicenseCore.token(k, deviceId)).apply()
        return true
    }
}

@Composable
fun LockScreen(license: LicenseStore, onActivated: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_logo_invoice), null,
                Modifier.size(88.dp), tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text("Activación requerida", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Ingresa la llave de uso para activar la aplicación en este dispositivo. " +
                    "Solo se pide una vez.",
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.uppercase().take(29); error = false },
                label = { Text("Llave de uso") },
                placeholder = { Text("XXXX-XXXX-XXXX-XXXX-XXXX-XXXX") },
                leadingIcon = { Icon(Icons.Filled.VpnKey, null) },
                isError = error,
                supportingText = { if (error) Text("Llave no válida para este dispositivo") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { if (license.activate(key)) onActivated() else error = true },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Activar", fontSize = 17.sp) }
            Spacer(Modifier.height(24.dp))
            Text("Código de este dispositivo", fontSize = 12.sp)
            Text(license.deviceCode, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "(si te piden una llave exclusiva para este teléfono, entrega este código)",
                fontSize = 11.sp, textAlign = TextAlign.Center
            )
        }
    }
}
