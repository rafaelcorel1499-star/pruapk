package com.lapaola.keygen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lapaola.common.LicenseCore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF7A2E2E))) {
                KeyGenScreen()
            }
        }
    }
}

private fun loadHistory(ctx: Context): List<String> =
    (ctx.getSharedPreferences("keys", Context.MODE_PRIVATE).getString("h", "") ?: "")
        .split("\n").filter { it.isNotBlank() }

private fun saveHistory(ctx: Context, list: List<String>) {
    ctx.getSharedPreferences("keys", Context.MODE_PRIVATE).edit().putString("h", list.joinToString("\n")).apply()
}

@Composable
fun KeyGenScreen() {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var device by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(loadHistory(ctx)) }

    Surface(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VpnKey, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Generador de llaves", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Text(
                    "Deja el código vacío para una llave general (sirve en cualquier teléfono). " +
                        "Si el cliente te da el \"Código de este dispositivo\" que aparece en su pantalla de activación, " +
                        "la llave será exclusiva de ese teléfono.",
                    fontSize = 13.sp
                )
            }
            item {
                OutlinedTextField(
                    value = device,
                    onValueChange = { device = it.uppercase().take(8); error = null },
                    label = { Text("Código del dispositivo (opcional)") },
                    placeholder = { Text("A1B2C3D4") },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = {
                        if (device.isNotBlank() && !LicenseCore.isValidDeviceCode(device)) {
                            error = "Deben ser 8 caracteres hexadecimales (0-9, A-F)"
                        } else {
                            val k = LicenseCore.generate(device.ifBlank { null })
                            val label = if (device.isBlank()) k else "$k  [$device]"
                            history = listOf(label) + history
                            saveHistory(ctx, history)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Generar llave", fontSize = 17.sp) }
            }
            item { Text("Llaves generadas", fontWeight = FontWeight.Bold) }
            itemsIndexed(history) { _, entry ->
                val key = entry.substringBefore(" ").trim()
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                        IconButton({ clipboard.setText(AnnotatedString(key)) }) { Icon(Icons.Filled.ContentCopy, "Copiar") }
                        IconButton({
                            val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, key) }
                            ctx.startActivity(Intent.createChooser(i, "Enviar llave"))
                        }) { Icon(Icons.Filled.Share, "Compartir") }
                    }
                }
            }
            if (history.isNotEmpty()) {
                item {
                    TextButton({ history = emptyList(); saveHistory(ctx, history) }) { Text("Borrar historial") }
                }
            }
        }
    }
}
