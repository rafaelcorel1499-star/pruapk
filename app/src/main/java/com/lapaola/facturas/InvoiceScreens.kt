@file:OptIn(ExperimentalMaterial3Api::class)

package com.lapaola.facturas

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// ---------------- Crear factura (con base de datos) ----------------

@Composable
fun NewInvoiceScreen(vm: MainViewModel) {
    var q by remember { mutableStateOf("") }
    val products by vm.products.collectAsState()
    val clients by vm.clients.collectAsState()
    val matches = products.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }.take(6)

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SellerPicker(vm) }
        item { ClientForm(vm.dbClient, clients) }
        item {
            Panel {
                SectionTitle(Icons.Filled.Inventory2, "Agregar Productos")
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    placeholder = { Text("Buscar producto en la base de datos…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (products.isEmpty()) Text("No hay productos. Agrégalos en «Base de Datos».", color = Color.Gray)
                matches.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, Modifier.weight(1f), fontSize = 16.sp)
                        Text(cop(p.price), fontSize = 16.sp)
                        FilledIconButton(onClick = { vm.addToCart(p) }, Modifier.padding(start = 8.dp).size(38.dp)) {
                            Icon(Icons.Filled.Add, "Agregar")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        item {
            Panel {
                SectionTitle(Icons.Filled.ShoppingCart, "Pedido")
                if (vm.cart.isEmpty()) Text("Aún no has agregado productos", color = Color.Gray)
                vm.cart.forEachIndexed { i, l ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${l.qty} x ${l.name}", fontSize = 15.sp)
                            Text(cop(l.total), fontSize = 14.sp, color = Color.Gray)
                        }
                        IconButton({ vm.changeQty(i, -1) }) { Icon(Icons.Filled.Remove, "Menos") }
                        Text("${l.qty}", fontWeight = FontWeight.Bold)
                        IconButton({ vm.changeQty(i, 1) }) { Icon(Icons.Filled.Add, "Más") }
                        IconButton({ vm.changeQty(i, -l.qty) }) { Icon(Icons.Filled.Delete, "Quitar") }
                    }
                }
            }
        }
        item {
            Panel {
                Text("TOTAL: ${cop(vm.cart.sumOf { it.total })} COP", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Button({ vm.openPreview(Source.Db) }, Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Print, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Generar e Imprimir Factura", fontSize = 16.sp)
                }
            }
        }
    }
}

// ---------------- Factura por voz ----------------

@Composable
fun VoiceInvoiceScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val clients by vm.clients.collectAsState()
    var listening by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }

    val dictation = remember {
        Dictation(
            ctx,
            onPartial = { heard = it },
            onFinal = { heard = it; vm.addVoice(it) },
            onFail = { vm.say(it) },
            onState = { listening = it }
        )
    }
    DisposableEffect(Unit) { onDispose { dictation.stop() } }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) dictation.start() else vm.say("Sin permiso de micrófono no se puede dictar")
    }

    fun toggle() {
        if (listening) { dictation.stop(); return }
        heard = ""
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            dictation.start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionTitle(Icons.Filled.Mic, "Factura por Voz")
        SellerPicker(vm)
        ClientForm(vm.voiceClient, clients)

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = { toggle() }, shape = CircleShape, contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(104.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (listening) Color(0xFFC62828) else MaterialTheme.colorScheme.primary)
            ) { Icon(Icons.Filled.Mic, "Dictar", Modifier.size(54.dp)) }
            Spacer(Modifier.height(6.dp))
            Text(if (listening) "Escuchando…" else "Toca el micrófono y dicta", fontWeight = FontWeight.Medium)
            if (heard.isNotBlank()) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(top = 6.dp)) {
                    Text("“$heard”", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 15.sp)
                }
            }
            Text(
                "Ejemplo: «4 panes de piña valor 2.500 pesos». El valor es por unidad.",
                fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
            )
        }

        OutlinedTextField(
            value = typed, onValueChange = { typed = it },
            placeholder = { Text("O escribe: banano maduro valor 600 pesos") },
            trailingIcon = { IconButton({ if (typed.isNotBlank() && vm.addVoice(typed)) typed = "" }) { Icon(Icons.Filled.Add, "Agregar") } },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ if (!listening) toggle() }, Modifier.weight(1f).height(50.dp)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Agregar Otro Producto", fontSize = 13.sp, maxLines = 2)
            }
            Button({ vm.openPreview(Source.Voice) }, Modifier.weight(1f).height(50.dp)) {
                Icon(Icons.Filled.Check, null); Spacer(Modifier.width(4.dp)); Text("Finalizar Factura", fontSize = 13.sp, maxLines = 2)
            }
        }

        Panel {
            SectionTitle(Icons.Filled.ShoppingCart, "Productos Dictados")
            if (vm.voiceLines.isEmpty()) Text("Aún no has dictado productos", color = Color.Gray)
            vm.voiceLines.forEachIndexed { i, l ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${l.name} x${l.qty}", fontSize = 16.sp)
                        if (l.qty > 1) Text("${cop(l.unitPrice)} c/u", fontSize = 12.sp, color = Color.Gray)
                    }
                    Text(cop(l.total), fontSize = 16.sp)
                    IconButton({ vm.voiceLines.removeAt(i) }) { Icon(Icons.Filled.Delete, "Quitar") }
                }
                HorizontalDivider()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TOTAL:", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(cop(vm.voiceLines.sumOf { it.total }), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------- Vista previa (tirilla) ----------------

@Composable
fun PreviewScreen(vm: MainViewModel, p: PreviewState) {
    val cols = if (vm.paperMm == 80) 48 else 32
    val lines = remember(p, cols, vm.businessName) { buildReceipt(p.data, cols, vm.businessName) }
    val fs = if (cols == 48) 10f else 12f

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        SectionTitle(Icons.Filled.Description, "Vista Previa de Factura")
        Card(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                val logo = vm.logo
                if (vm.logoOnReceipt && logo != null) {
                    Image(
                        bitmap = logo.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(max = 90.dp).padding(bottom = 6.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                lines.forEach { l ->
                    Text(
                        l.text, Modifier.fillMaxWidth(), color = Color.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (if (l.big) fs * 2 else fs).sp,
                        fontWeight = if (l.bold || l.big) FontWeight.Bold else FontWeight.Normal,
                        textAlign = if (l.center) TextAlign.Center else TextAlign.Start,
                        maxLines = 1, softWrap = false
                    )
                }
            }
        }
        Button(
            onClick = { if (p.source != null) vm.confirm(true) else vm.printPreview() },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Filled.Print, null); Spacer(Modifier.width(8.dp))
            Text(if (p.source != null) "Imprimir y Guardar" else "Imprimir", fontSize = 17.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (p.source != null) {
            OutlinedButton({ vm.confirm(false) }, Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text("Solo Guardar", fontSize = 16.sp)
            }
        }
        TextButton({ vm.preview = null }, Modifier.fillMaxWidth()) {
            Text(if (p.source != null) "Volver y editar" else "Cerrar")
        }
    }
}

// ---------------- Facturas generadas ----------------

@Composable
fun InvoicesScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = vm.invoicesTab) {
            Tab(selected = vm.invoicesTab == 0, onClick = { vm.invoicesTab = 0 }, text = { Text("FACTURAS") })
            Tab(selected = vm.invoicesTab == 1, onClick = { vm.invoicesTab = 1 }, text = { Text("VENTAS") })
        }
        if (vm.invoicesTab == 0) InvoiceListTab(vm) else SalesScreen(vm)
    }
}

@Composable
private fun InvoiceListTab(vm: MainViewModel) {
    var q by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Invoice?>(null) }
    val all by vm.invoices.collectAsState()
    val key = q.trim().removePrefix("#")
    val list = all.filter {
        key.isBlank() || it.clientName.contains(key, ignoreCase = true) ||
            invNumber(it.id).contains(key) || fmtDate(it.dateMillis).contains(key, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        SectionTitle(Icons.Filled.Description, "Facturas Generadas")
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            placeholder = { Text("Buscar por cliente, fecha o número…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Button({ vm.exportExcel(list) }, Modifier.fillMaxWidth().height(46.dp).padding(bottom = 4.dp)) {
            Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp))
            Text("EXPORTAR EXCEL DESCARGAS", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        if (list.isEmpty()) {
            Text(if (all.isEmpty()) "Todavía no hay facturas." else "Sin resultados.", color = Color.Gray, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(list, key = { it.id }) { inv ->
                Card(
                    Modifier.fillMaxWidth().clickable { vm.openSaved(inv) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("#${invNumber(inv.id)}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(fmtDate(inv.dateMillis), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(inv.clientName, fontSize = 14.sp, maxLines = 1)
                            if (inv.seller.isNotBlank()) Text("Vendedor: ${inv.seller}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Pago: ${payLabel(inv.paymentMethod)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total:", fontSize = 12.sp)
                            Text(cop(inv.total), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Row {
                                IconButton({ vm.printSaved(inv) }) { Icon(Icons.Filled.Print, "Imprimir") }
                                IconButton({ toDelete = inv }) { Icon(Icons.Filled.Delete, "Eliminar") }
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { inv ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminar factura") },
            text = { Text("¿Eliminar la factura #${invNumber(inv.id)} de ${inv.clientName}?") },
            confirmButton = { TextButton({ vm.deleteInvoice(inv); toDelete = null }) { Text("Eliminar") } },
            dismissButton = { TextButton({ toDelete = null }) { Text("Cancelar") } }
        )
    }
}
