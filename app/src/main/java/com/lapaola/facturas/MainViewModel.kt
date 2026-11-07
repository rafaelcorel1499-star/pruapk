package com.lapaola.facturas

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen { Home, Invoices, New, Voice, Products, Settings }
enum class Source { Db, Voice }

class ClientState {
    var name by mutableStateOf("")
    var phone by mutableStateOf("")
    var address by mutableStateOf("")
    var payment by mutableStateOf(PAYMENT_METHODS[0])
    fun clear() { name = ""; phone = ""; address = ""; payment = PAYMENT_METHODS[0] }
}

/** source = null -> factura ya guardada (solo se puede imprimir). */
data class PreviewState(val data: ReceiptData, val source: Source?)

/** "#RRGGBB" (con o sin #) -> Color, o null si no es válido. */
fun parseHex(s: String): Color? {
    val h = s.trim().removePrefix("#")
    if (h.length != 6 || !h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return Color(0xFF000000L or h.toLong(16))
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.build(app).dao()
    val settings = Settings(app)

    val products = dao.products().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<Product>())
    val employees = dao.employees().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<Employee>())
    val invoices = dao.invoices().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<Invoice>())
    val clients = dao.clients().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<Invoice>())

    var screen by mutableStateOf(Screen.Home)
    var invoicesTab by mutableStateOf(0)
    var preview by mutableStateOf<PreviewState?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    fun say(m: String) { messages.tryEmit(m) }
    fun go(s: Screen) { preview = null; screen = s }

    val dbClient = ClientState()
    val voiceClient = ClientState()
    val cart = mutableStateListOf<CartLine>()
    val voiceLines = mutableStateListOf<CartLine>()

    // ----- configuración (valores guardados) -----
    var printerIp by mutableStateOf(settings.printerIp)
    var paperMm by mutableStateOf(settings.paperMm)
    var noAccents by mutableStateOf(settings.noAccents)
    var businessName by mutableStateOf(settings.businessName)
    var logoOnReceipt by mutableStateOf(settings.logoOnReceipt)
    var darkMode by mutableStateOf(settings.darkMode)
    var primaryHex by mutableStateOf(settings.primaryHex)
    var secondaryHex by mutableStateOf(settings.secondaryHex)
    var logo by mutableStateOf<Bitmap?>(loadLogo())
    var seller by mutableStateOf(settings.lastSeller)
    val found = MutableStateFlow<List<String>>(emptyList())
    var scanning by mutableStateOf(false)

    private fun logoFile() = File(getApplication<Application>().filesDir, "logo.png")

    private fun loadLogo(): Bitmap? =
        logoFile().takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

    fun setLogo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val src = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it) } ?: error("sin imagen")
                val maxW = 600
                val bmp = if (src.width > maxW)
                    Bitmap.createScaledBitmap(src, maxW, (src.height * maxW.toFloat() / src.width).toInt().coerceAtLeast(1), true)
                else src
                logoFile().outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                logo = bmp
            }.onSuccess { say("Logo guardado") }
                .onFailure { say("No se pudo cargar la imagen") }
        }
    }

    fun removeLogo() {
        logoFile().delete()
        logo = null
        say("Logo eliminado")
    }

    fun toggleDark() {
        darkMode = !darkMode
        settings.darkMode = darkMode
    }

    fun scan() {
        if (scanning) return
        scanning = true
        found.value = emptyList()
        viewModelScope.launch {
            runCatching { Printer.discover { ip -> found.update { (it + ip).distinct().sorted() } } }
            scanning = false
        }
    }

    fun saveSettings(
        ip: String, mm: Int, noAcc: Boolean,
        name: String, logoReceipt: Boolean, primary: String, secondary: String
    ) {
        if (parseHex(primary) == null || parseHex(secondary) == null) {
            say("Color no válido. Usa el formato #RRGGBB, por ejemplo #FF0000")
            return
        }
        val p = "#" + primary.trim().removePrefix("#").uppercase()
        val s = "#" + secondary.trim().removePrefix("#").uppercase()
        val n = name.trim().ifBlank { "Mi Negocio" }
        settings.printerIp = ip.trim(); settings.paperMm = mm; settings.noAccents = noAcc
        settings.businessName = n; settings.logoOnReceipt = logoReceipt
        settings.primaryHex = p; settings.secondaryHex = s
        printerIp = ip.trim(); paperMm = mm; noAccents = noAcc
        businessName = n; logoOnReceipt = logoReceipt
        primaryHex = p; secondaryHex = s
        say("Cambios guardados")
    }

    fun testPrint(ip: String, mm: Int, noAcc: Boolean) {
        val d = ReceiptData(
            1, System.currentTimeMillis(), "Prueba de impresión", "3001234567", "Calle 10 #5-23",
            listOf(CartLine("Producto de prueba", 2, 2500), CartLine("Otro producto", 1, 600)),
            currentSeller()
        )
        viewModelScope.launch { doPrint(d, ip.trim(), mm, noAcc) }
    }

    private suspend fun doPrint(
        d: ReceiptData,
        ip: String = settings.printerIp,
        mm: Int = settings.paperMm,
        noAcc: Boolean = settings.noAccents
    ) {
        if (ip.isBlank()) { say("Primero selecciona una impresora en Configuración"); return }
        val bytes = Printer.build(
            buildReceipt(d, if (mm == 80) 48 else 32, businessName),
            noAcc, if (logoOnReceipt) logo else null, mm
        )
        runCatching { Printer.send(ip, bytes) }
            .onSuccess { say("Enviado a la impresora") }
            .onFailure { say("No se pudo imprimir: ${it.message ?: "revisa la conexión"}") }
    }

    // ----- empleados / vendedor -----
    /** Vendedor seleccionado: el último usado si sigue existiendo; si no, el primer empleado. */
    fun currentSeller(): String =
        employees.value.firstOrNull { it.name == seller }?.name ?: employees.value.firstOrNull()?.name ?: ""

    fun addEmployee(name: String) {
        val n = name.trim()
        if (n.isBlank()) { say("Escribe el nombre del empleado"); return }
        if (employees.value.any { it.name.equals(n, ignoreCase = true) }) { say("Ese empleado ya existe"); return }
        viewModelScope.launch {
            dao.insertEmployee(Employee(name = n))
            if (employees.value.isEmpty()) seller = n
        }
    }

    fun deleteEmployee(e: Employee) { viewModelScope.launch { dao.deleteEmployee(e.id) } }

    // ----- productos -----
    fun addProduct(name: String, price: Long, stock: Int) {
        viewModelScope.launch { dao.insertProduct(Product(name = name, price = price, stock = stock)) }
    }
    fun deleteProduct(p: Product) { viewModelScope.launch { dao.deleteProduct(p.id) } }
    fun setStock(p: Product, stock: Int) { viewModelScope.launch { dao.setStock(p.id, stock) } }

    // ----- carrito (base de datos) -----
    fun addToCart(p: Product) {
        val i = cart.indexOfFirst { it.name == p.name && it.unitPrice == p.price }
        val inCart = if (i >= 0) cart[i].qty else 0
        if (inCart + 1 > p.stock) say("Existencias insuficientes de ${p.name} (hay ${p.stock})")
        if (i >= 0) cart[i] = cart[i].copy(qty = cart[i].qty + 1) else cart.add(CartLine(p.name, 1, p.price))
    }

    fun changeQty(i: Int, d: Int) {
        val n = cart[i].qty + d
        if (n <= 0) cart.removeAt(i) else cart[i] = cart[i].copy(qty = n)
    }

    // ----- voz -----
    fun addVoice(text: String): Boolean {
        val l = parseDictation(text)
        if (l == null) {
            say("No entendí «$text». Di por ejemplo: 4 panes de piña valor 2500 pesos")
            return false
        }
        voiceLines.add(l)
        say("Agregado: ${l.qty} x ${l.name} a ${cop(l.unitPrice)}")
        return true
    }

    // ----- facturas -----
    fun openPreview(source: Source) {
        val c = if (source == Source.Db) dbClient else voiceClient
        val lines = (if (source == Source.Db) cart else voiceLines).toList()
        if (lines.isEmpty()) { say("Agrega al menos un producto"); return }
        preview = PreviewState(
            ReceiptData(
                null, System.currentTimeMillis(), c.name.trim().ifBlank { "Cliente general" },
                c.phone.trim(), c.address.trim(), lines, currentSeller(), c.payment
            ), source
        )
    }

    fun confirm(print: Boolean) {
        val p = preview ?: return
        val src = p.source ?: return
        viewModelScope.launch {
            val d = p.data
            val id = dao.saveInvoice(
                Invoice(
                    dateMillis = d.dateMillis, clientName = d.client, phone = d.phone,
                    address = d.address, total = d.total, seller = d.seller,
                    paymentMethod = d.payment
                ),
                d.lines.map { InvoiceItem(invoiceId = 0, name = it.name, qty = it.qty, unitPrice = it.unitPrice) }
            )
            if (d.seller.isNotBlank()) { seller = d.seller; settings.lastSeller = d.seller }
            if (src == Source.Db) { cart.clear(); dbClient.clear() } else { voiceLines.clear(); voiceClient.clear() }
            preview = null
            screen = Screen.Invoices
            invoicesTab = 0
            say("Factura #${invNumber(id)} guardada")
            if (print) doPrint(d.copy(number = id))
        }
    }

    private suspend fun dataOf(inv: Invoice): ReceiptData {
        val items = dao.itemsOf(inv.id)
        return ReceiptData(
            inv.id, inv.dateMillis, inv.clientName, inv.phone, inv.address,
            items.map { CartLine(it.name, it.qty, it.unitPrice) }, inv.seller, inv.paymentMethod
        )
    }

    suspend fun itemsFor(ids: List<Long>): List<InvoiceItem> =
        ids.chunked(500).flatMap { dao.itemsOfMany(it) }

    /** Exporta a Excel (carpeta Descargas) las facturas recibidas: una fila por producto vendido. */
    fun exportExcel(list: List<Invoice>) {
        if (list.isEmpty()) { say("No hay ventas para exportar con estos filtros"); return }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val items = itemsFor(list.map { it.id }).groupBy { it.invoiceId }
                    val rows = list.sortedWith(compareBy({ it.dateMillis }, { it.id })).flatMap { inv ->
                        (items[inv.id] ?: emptyList()).map { it ->
                            ExcelRow(
                                inv.dateMillis,
                                if (it.qty > 1) "${it.name} x${it.qty}" else it.name,
                                inv.seller, it.qty * it.unitPrice
                            )
                        }
                    }
                    val bytes = ExcelExport.build(
                        businessName, employees.value.sortedBy { it.id }.map { it.name }, rows
                    )
                    val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
                    ExcelExport.saveToDownloads(getApplication<Application>(), "Ventas_$stamp.xlsx", bytes)
                }
            }
            result.onSuccess { say("Excel guardado en Descargas: $it") }
                .onFailure { say("No se pudo exportar: ${it.message ?: "error desconocido"}") }
        }
    }

    fun openSaved(inv: Invoice) { viewModelScope.launch { preview = PreviewState(dataOf(inv), null) } }
    fun printSaved(inv: Invoice) { viewModelScope.launch { doPrint(dataOf(inv)) } }
    fun printPreview() { val p = preview ?: return; viewModelScope.launch { doPrint(p.data) } }
    fun deleteInvoice(inv: Invoice) { viewModelScope.launch { dao.deleteInvoice(inv.id); say("Factura eliminada") } }
}
