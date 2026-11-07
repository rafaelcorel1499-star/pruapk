@file:OptIn(ExperimentalMaterial3Api::class)

package com.lapaola.facturas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------- Base de datos de productos ----------------

@Composable
fun ProductsScreen(vm: MainViewModel) {
    val products by vm.products.collectAsState()
    var q by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Product?>(null) }
    var toStock by remember { mutableStateOf<Product?>(null) }
    var showEmployees by remember { mutableStateOf(false) }
    val list = products.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SectionTitle(Icons.Filled.Storage, "Base de Datos - Productos") }
        item {
            OutlinedButton({ showEmployees = true }, Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Filled.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Agregar Empleado", fontSize = 16.sp)
            }
        }
        item {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, placeholder = { Text("Buscar producto…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Panel {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Nombre del Producto") },
                    leadingIcon = { Icon(Icons.Filled.Sell, null) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price, onValueChange = { price = it.filter { c -> c.isDigit() } },
                    label = { Text("Valor en Pesos Colombianos (COP)") },
                    leadingIcon = { Icon(Icons.Filled.AttachMoney, null) },
                    supportingText = { price.toLongOrNull()?.let { Text(cop(it)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = stock, onValueChange = { stock = it.filter { c -> c.isDigit() }.take(7) },
                    label = { Text("Cantidad en existencias") },
                    leadingIcon = { Icon(Icons.Filled.Inventory2, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Button({
                    val v = price.toLongOrNull()
                    if (name.isBlank() || v == null || v <= 0) vm.say("Escribe el nombre y el valor")
                    else {
                        vm.addProduct(name.trim(), v, stock.toIntOrNull() ?: 0)
                        name = ""; price = ""; stock = ""; vm.say("Producto guardado")
                    }
                }, Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Agregar Producto", fontSize = 16.sp)
                }
            }
        }
        items(list, key = { it.id }) { p ->
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Existencias: ${p.stock}", fontSize = 13.sp,
                            color = if (p.stock <= 0) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(cop(p.price), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton({ toStock = p }) { Icon(Icons.Filled.Edit, "Editar existencias") }
                    IconButton({ toDelete = p }) { Icon(Icons.Filled.Delete, "Eliminar") }
                }
            }
        }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminar producto") },
            text = { Text("¿Eliminar «${p.name}» de la base de datos?") },
            confirmButton = { TextButton({ vm.deleteProduct(p); toDelete = null }) { Text("Eliminar") } },
            dismissButton = { TextButton({ toDelete = null }) { Text("Cancelar") } }
        )
    }

    toStock?.let { p ->
        var v by remember(p.id) { mutableStateOf(p.stock.toString()) }
        AlertDialog(
            onDismissRequest = { toStock = null },
            title = { Text("Existencias de ${p.name}") },
            text = {
                OutlinedTextField(
                    value = v, onValueChange = { v = it.filter { c -> c.isDigit() }.take(7) },
                    label = { Text("Cantidad disponible") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = { TextButton({ vm.setStock(p, v.toIntOrNull() ?: 0); toStock = null }) { Text("Guardar") } },
            dismissButton = { TextButton({ toStock = null }) { Text("Cancelar") } }
        )
    }

    if (showEmployees) EmployeesDialog(vm) { showEmployees = false }
}

@Composable
private fun EmployeesDialog(vm: MainViewModel, onClose: () -> Unit) {
    val employees by vm.employees.collectAsState()
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Empleados") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Nombre del empleado") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(
                        onClick = { vm.addEmployee(name); name = "" },
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Icon(Icons.Filled.Add, "Agregar empleado") }
                }
                if (employees.isEmpty()) Text("Aún no hay empleados", color = Color.Gray)
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(employees, key = { it.id }) { e ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Person, null)
                            Text(e.name, Modifier.weight(1f).padding(start = 8.dp), fontSize = 16.sp)
                            IconButton({ vm.deleteEmployee(e) }) { Icon(Icons.Filled.Delete, "Eliminar") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Cerrar") } }
    )
}

// ---------------- Configuración ----------------

@Composable
fun SettingsScreen(vm: MainViewModel) {
    var ip by remember { mutableStateOf(vm.printerIp) }
    var mm by remember { mutableStateOf(vm.paperMm) }
    var noAcc by remember { mutableStateOf(vm.noAccents) }
    var bizName by remember { mutableStateOf(vm.businessName) }
    var logoReceipt by remember { mutableStateOf(vm.logoOnReceipt) }
    var primary by remember { mutableStateOf(vm.primaryHex) }
    var secondary by remember { mutableStateOf(vm.secondaryHex) }
    var openPrinter by remember { mutableStateOf(false) }
    var openPaper by remember { mutableStateOf(false) }
    val found by vm.found.collectAsState()
    LaunchedEffect(Unit) { vm.scan() }
    val options = (found + listOfNotNull(ip.takeIf { it.isNotBlank() })).distinct()
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setLogo(uri)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ----- Negocio -----
        SectionTitle(Icons.Filled.Storefront, "Datos del Negocio")
        OutlinedTextField(
            value = bizName, onValueChange = { bizName = it },
            label = { Text("Nombre del negocio") },
            supportingText = { Text("Aparece en el menú superior y en la factura") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val logo = vm.logo
            if (logo != null) {
                Surface(shape = RoundedCornerShape(10.dp), color = Color.White, shadowElevation = 1.dp) {
                    Image(
                        bitmap = logo.asImageBitmap(), contentDescription = "Logo",
                        modifier = Modifier.padding(4.dp).size(64.dp), contentScale = ContentScale.Fit
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                OutlinedButton({ pickLogo.launch("image/png") }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Image, null); Spacer(Modifier.width(8.dp))
                    Text(if (logo == null) "Agregar logo (.png)" else "Cambiar logo")
                }
                if (logo != null) {
                    TextButton({ vm.removeLogo() }) { Text("Quitar logo") }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = logoReceipt, onCheckedChange = { logoReceipt = it }, enabled = vm.logo != null)
            Text("Mostrar el logo en la parte superior de la factura, junto al nombre", fontSize = 14.sp)
        }

        HorizontalDivider()
        SectionTitle(Icons.Filled.Palette, "Colores de la App")
        HexField("Color primario (barras y botones)", primary) { primary = it }
        HexField("Color secundario (letras e iconos)", secondary) { secondary = it }
        Text("Escribe el código hexadecimal, por ejemplo #FF0000. El modo oscuro se activa con la luna del menú superior.", fontSize = 12.sp, color = Color.Gray)

        HorizontalDivider()
        SectionTitle(Icons.Filled.Print, "Impresora Térmica en Red")
        ExposedDropdownMenuBox(expanded = openPrinter, onExpandedChange = { openPrinter = it }) {
            OutlinedTextField(
                value = if (ip.isBlank()) "" else "Impresora térmica ($ip)",
                onValueChange = {}, readOnly = true,
                placeholder = { Text("Selecciona una impresora") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(openPrinter) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = openPrinter, onDismissRequest = { openPrinter = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text("Impresora térmica ($o)") }, onClick = { ip = o; openPrinter = false })
                }
                if (options.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(if (vm.scanning) "Buscando…" else "No se encontraron impresoras") },
                        onClick = { openPrinter = false }, enabled = false
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (vm.scanning) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Buscando impresoras en la red…", fontSize = 14.sp)
            } else {
                Icon(Icons.Filled.Search, null)
                TextButton({ vm.scan() }) { Text("Buscar impresoras de nuevo") }
            }
        }
        OutlinedTextField(
            value = ip, onValueChange = { ip = it.trim() },
            label = { Text("O escribe la IP manualmente") }, placeholder = { Text("192.168.1.15") },
            supportingText = { Text("Puerto RAW 9100 (por defecto). Celular e impresora en la misma red Wi-Fi.") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()
        SectionTitle(Icons.Filled.Description, "Tamaño de Papel")
        ExposedDropdownMenuBox(expanded = openPaper, onExpandedChange = { openPaper = it }) {
            OutlinedTextField(
                value = "$mm mm", onValueChange = {}, readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(openPaper) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = openPaper, onDismissRequest = { openPaper = false }) {
                listOf(80, 57).forEach { o ->
                    DropdownMenuItem(text = { Text("$o mm") }, onClick = { mm = o; openPaper = false })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = noAcc, onCheckedChange = { noAcc = it })
            Text("Imprimir sin tildes (si tu impresora muestra símbolos raros)", fontSize = 14.sp)
        }

        Button(
            { vm.saveSettings(ip, mm, noAcc, bizName, logoReceipt && vm.logo != null, primary, secondary) },
            Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text("Guardar Cambios", fontSize = 17.sp)
        }
        OutlinedButton({ vm.testPrint(ip, mm, noAcc) }, Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Filled.Print, null); Spacer(Modifier.width(8.dp)); Text("Imprimir prueba")
        }
    }
}

@Composable
private fun HexField(label: String, value: String, onChange: (String) -> Unit) {
    val color = parseHex(value)
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.trim().take(7)) },
        label = { Text(label) }, placeholder = { Text("#FF0000") },
        isError = color == null,
        supportingText = { if (color == null) Text("Formato: #RRGGBB") },
        trailingIcon = {
            Box(Modifier.size(28.dp).background(color ?: Color.Gray, CircleShape))
        },
        singleLine = true, modifier = Modifier.fillMaxWidth()
    )
}
