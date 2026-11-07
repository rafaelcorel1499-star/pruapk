@file:OptIn(ExperimentalMaterial3Api::class)

package com.lapaola.facturas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun startOfDay(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun nextDay(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.add(Calendar.DAY_OF_MONTH, 1)
    return c.timeInMillis
}

/** El selector de fecha trabaja en UTC; la app trabaja en hora local. */
private fun localToPicker(ms: Long): Long {
    val l = Calendar.getInstance(); l.timeInMillis = ms
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")); u.clear()
    u.set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
    return u.timeInMillis
}

private fun pickerToLocal(ms: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")); u.timeInMillis = ms
    val l = Calendar.getInstance(); l.clear()
    l.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
    return l.timeInMillis
}

private fun fmtDay(ms: Long): String = SimpleDateFormat("EEEE dd/MM/yyyy", Locale("es", "CO")).format(Date(ms))
private fun fmtHour(ms: Long): String = SimpleDateFormat("hh:mm a", Locale.US).format(Date(ms))

private const val NO_SELLER = "Sin vendedor"

@Composable
fun SalesScreen(vm: MainViewModel) {
    val all by vm.invoices.collectAsState()
    val employees by vm.employees.collectAsState()
    var day by remember { mutableStateOf(startOfDay(System.currentTimeMillis())) }
    var allDays by remember { mutableStateOf(false) }
    var sellerFilter by remember { mutableStateOf("") } // "" = todos
    var showPicker by remember { mutableStateOf(false) }
    var sellerOpen by remember { mutableStateOf(false) }
    var payFilter by remember { mutableStateOf("") } // "" = todos; NO_PAYMENT = facturas antiguas sin método
    var payOpen by remember { mutableStateOf(false) }

    val sellerOptions = (employees.map { it.name } + all.map { it.seller }.filter { it.isNotBlank() })
        .distinct().sortedBy { it.lowercase() }

    val list = all.filter { inv ->
        (allDays || (inv.dateMillis >= day && inv.dateMillis < nextDay(day))) &&
            (sellerFilter.isEmpty() || inv.seller == sellerFilter) &&
            (payFilter.isEmpty() || payLabel(inv.paymentMethod) == payFilter)
    }
    val payOptions = PAYMENT_METHODS + (if (all.any { it.paymentMethod.isBlank() }) listOf(NO_PAYMENT) else emptyList())
    val total = list.sumOf { it.total }
    val soldItems by produceState(emptyList<InvoiceItem>(), list) { value = vm.itemsFor(list.map { it.id }) }

    val bySeller = list.groupBy { it.seller.ifBlank { NO_SELLER } }
        .map { (k, v) -> Triple(k, v.size, v.sumOf { it.total }) }
        .sortedByDescending { it.third }
    val byPayment = list.groupBy { payLabel(it.paymentMethod) }
        .map { (k, v) -> Triple(k, v.size, v.sumOf { it.total }) }
        .sortedByDescending { it.third }
    val byProduct = soldItems.groupBy { it.name.lowercase() }
        .map { (_, v) -> Triple(v.first().name, v.sumOf { it.qty }, v.sumOf { it.qty * it.unitPrice }) }
        .sortedByDescending { it.third }

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Panel {
                SectionTitle(Icons.Filled.FilterList, "Filtros")
                OutlinedButton({ showPicker = true }, Modifier.fillMaxWidth(), enabled = !allDays) {
                    Icon(Icons.Filled.CalendarMonth, null); Spacer(Modifier.width(8.dp))
                    Text(if (allDays) "Todos los días" else fmtDay(day).replaceFirstChar { it.uppercase() })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allDays, onCheckedChange = { allDays = it })
                    Text("Ver todos los días", fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton({ day = startOfDay(System.currentTimeMillis()); allDays = false }) { Text("Hoy") }
                }
                ExposedDropdownMenuBox(expanded = sellerOpen, onExpandedChange = { sellerOpen = it }) {
                    OutlinedTextField(
                        value = sellerFilter.ifEmpty { "Todos los vendedores" }, onValueChange = {}, readOnly = true,
                        label = { Text("Vendedor") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sellerOpen) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = sellerOpen, onDismissRequest = { sellerOpen = false }) {
                        DropdownMenuItem(text = { Text("Todos los vendedores") }, onClick = { sellerFilter = ""; sellerOpen = false })
                        sellerOptions.forEach { n ->
                            DropdownMenuItem(text = { Text(n) }, onClick = { sellerFilter = n; sellerOpen = false })
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = payOpen, onExpandedChange = { payOpen = it }) {
                    OutlinedTextField(
                        value = payFilter.ifEmpty { "Todos los métodos de pago" }, onValueChange = {}, readOnly = true,
                        label = { Text("Método de pago") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(payOpen) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = payOpen, onDismissRequest = { payOpen = false }) {
                        DropdownMenuItem(text = { Text("Todos los métodos de pago") }, onClick = { payFilter = ""; payOpen = false })
                        payOptions.forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = { payFilter = m; payOpen = false })
                        }
                    }
                }
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val cs = MaterialTheme.colorScheme
                    Text("TOTAL VENDIDO", fontSize = 13.sp, color = cs.onPrimary)
                    Text(cop(total), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary)
                    Text(
                        "${list.size} factura${if (list.size == 1) "" else "s"} · " +
                            (if (allDays) "todos los días" else fmtDay(day)) +
                            (if (sellerFilter.isNotEmpty()) " · $sellerFilter" else "") +
                            (if (payFilter.isNotEmpty()) " · $payFilter" else ""),
                        fontSize = 13.sp, color = cs.onPrimary
                    )
                }
            }
        }

        item {
            Button({ vm.exportExcel(list) }, Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp))
                Text("EXPORTAR EXCEL DESCARGAS", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (payFilter.isEmpty() && byPayment.isNotEmpty()) {
            item {
                Panel {
                    SectionTitle(Icons.Filled.Payments, "Ventas por método de pago")
                    byPayment.forEach { (n, count, sum) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { payFilter = n },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(n, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text("$count factura${if (count == 1) "" else "s"}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Text(cop(sum), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        if (sellerFilter.isEmpty() && bySeller.isNotEmpty()) {
            item {
                Panel {
                    SectionTitle(Icons.Filled.Badge, "Ventas por vendedor")
                    bySeller.forEach { (n, count, sum) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(n, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text("$count factura${if (count == 1) "" else "s"}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Text(cop(sum), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        if (byProduct.isNotEmpty()) {
            item {
                Panel {
                    SectionTitle(Icons.Filled.Inventory2, "Productos vendidos")
                    byProduct.forEach { (n, qty, sum) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("$qty x $n", Modifier.weight(1f), fontSize = 15.sp)
                            Text(cop(sum), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        item { SectionTitle(Icons.Filled.Description, "Detalle de facturas") }
        if (list.isEmpty()) {
            item { Text("No hay ventas con estos filtros.", color = Color.Gray, modifier = Modifier.padding(8.dp)) }
        }
        items(list, key = { it.id }) { inv ->
            Card(
                Modifier.fillMaxWidth().clickable { vm.openSaved(inv) }, shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("#${invNumber(inv.id)}  ·  ${fmtHour(inv.dateMillis)}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(inv.clientName, fontSize = 13.sp, maxLines = 1)
                        Text(
                            "Vendedor: ${inv.seller.ifBlank { NO_SELLER }}  ·  Pago: ${payLabel(inv.paymentMethod)}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(cop(inv.total), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showPicker) {
        val st = rememberDatePickerState(initialSelectedDateMillis = localToPicker(day))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { day = pickerToLocal(it); allDays = false }
                    showPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton({ showPicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = st) }
    }
}
