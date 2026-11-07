package com.lapaola.facturas

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CartLine(val name: String, val qty: Int, val unitPrice: Long) {
    val total: Long get() = qty * unitPrice
}

data class ReceiptData(
    val number: Long?,
    val dateMillis: Long,
    val client: String,
    val phone: String,
    val address: String,
    val lines: List<CartLine>,
    val seller: String = "",
    val payment: String = ""
) {
    val total: Long get() = lines.sumOf { it.total }
}

/** Una línea de la tirilla (sirve para la vista previa y para ESC/POS). */
data class RLine(
    val text: String,
    val center: Boolean = false,
    val bold: Boolean = false,
    val big: Boolean = false
)

private val money = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US).apply { groupingSeparator = '.' })

fun cop(v: Long): String = "\$" + money.format(v)
fun invNumber(n: Long): String = n.toString().padStart(5, '0')
fun fmtDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).format(Date(ms))

fun wrapText(text: String, width: Int): List<String> {
    val out = mutableListOf<String>()
    var cur = ""
    for (word in text.split(" ").filter { it.isNotEmpty() }) {
        var w = word
        while (w.length > width) {
            if (cur.isNotEmpty()) { out.add(cur); cur = "" }
            out.add(w.take(width))
            w = w.drop(width)
        }
        cur = when {
            cur.isEmpty() -> w
            cur.length + 1 + w.length <= width -> "$cur $w"
            else -> { out.add(cur); w }
        }
    }
    if (cur.isNotEmpty()) out.add(cur)
    return if (out.isEmpty()) listOf("") else out
}

fun buildReceipt(r: ReceiptData, cols: Int, businessName: String): List<RLine> {
    val out = mutableListOf<RLine>()
    val dash = RLine("-".repeat(cols))

    // Nombre del negocio: letra grande y partido en varias líneas si es largo
    wrapText(businessName.trim().uppercase().ifBlank { "FACTURA" }, maxOf(8, cols / 2)).forEach {
        out.add(RLine(it, center = true, big = true))
    }
    out.add(dash)
    r.number?.let { out.add(RLine("Factura No. ${invNumber(it)}")) }
    out.add(RLine("Fecha: ${fmtDate(r.dateMillis)}"))
    out.add(dash)
    wrapText("Cliente: ${r.client}", cols).forEach { out.add(RLine(it)) }
    if (r.seller.isNotBlank()) wrapText("Vendedor: ${r.seller}", cols).forEach { out.add(RLine(it)) }
    if (r.payment.isNotBlank()) wrapText("Pago: ${r.payment}", cols).forEach { out.add(RLine(it)) }
    if (r.phone.isNotBlank()) wrapText("Tel: ${r.phone}", cols).forEach { out.add(RLine(it)) }
    if (r.address.isNotBlank()) wrapText("Dir: ${r.address}", cols).forEach { out.add(RLine(it)) }
    out.add(dash)

    for (l in r.lines) {
        val price = cop(l.total)
        val chunks = wrapText("${l.qty} x ${l.name}", maxOf(8, cols - price.length - 1))
        out.add(RLine(chunks[0].padEnd(cols - price.length) + price))
        chunks.drop(1).forEach { out.add(RLine("   $it")) }
        if (l.qty > 1) out.add(RLine("   (${cop(l.unitPrice)} c/u)"))
    }

    out.add(dash)
    val tp = cop(r.total)
    val half = cols / 2
    if (5 + 1 + tp.length <= half) {
        out.add(RLine("TOTAL".padEnd(half - tp.length) + tp, bold = true, big = true))
    } else {
        out.add(RLine("TOTAL".padEnd(cols - tp.length) + tp, bold = true))
    }
    out.add(dash)
    out.add(RLine("¡Gracias por su compra!", center = true))
    out.add(RLine("Vuelva pronto", center = true))
    return out
}
