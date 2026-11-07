package com.lapaola.facturas

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Una fila del Excel = un producto vendido. [seller] decide en qué columna va el valor. */
data class ExcelRow(val dateMillis: Long, val description: String, val seller: String, val amount: Long)

/**
 * Genera un .xlsx con la estructura de la plantilla:
 *   FECHA | DESCRIPCION | una columna por trabajador (LOCAL, MIGUE, YOIMAR…)
 * Sin librerías externas: un .xlsx es un zip con XML.
 */
object ExcelExport {
    private const val NO_SELLER = "SIN VENDEDOR"
    private const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").filter { it.code >= 32 || it == '\n' }

    private fun col(i: Int): String { // 0 -> A
        var n = i; var s = ""
        do { s = ('A' + n % 26) + s; n = n / 26 - 1 } while (n >= 0)
        return s
    }

    private fun serial(ms: Long): Long =
        ChronoUnit.DAYS.between(
            LocalDate.of(1899, 12, 30),
            java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
        )

    // Estilos (índices de cellXfs en styles.xml)
    private const val S_TITLE = 1
    private const val S_HEAD = 2
    private const val S_DATE = 3
    private const val S_TEXT = 4
    private const val S_MONEY = 5
    private const val S_TOT_LABEL = 6
    private const val S_TOT_MONEY = 7

    fun build(businessName: String, employeeNames: List<String>, rows: List<ExcelRow>): ByteArray {
        // Columnas: trabajadores registrados (en orden de registro) + cualquier vendedor usado que ya no exista
        val cols = employeeNames.map { it.trim().uppercase() }.distinct().toMutableList()
        rows.forEach { r ->
            val n = r.seller.trim().uppercase().ifBlank { NO_SELLER }
            if (n !in cols) cols.add(n)
        }
        if (cols.isEmpty()) cols.add(NO_SELLER)

        val first = 1                      // B
        val lastCol = first + 1 + cols.size // última columna de trabajadores
        val headRow = 2
        val firstData = 3
        val lastData = firstData + rows.size - 1
        val totalRow = firstData + rows.size

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetViews><sheetView workbookViewId="0" showGridLines="0"><pane ySplit="2" topLeftCell="A3" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sb.append("""<cols><col min="1" max="1" width="3" customWidth="1"/><col min="2" max="2" width="14" customWidth="1"/><col min="3" max="3" width="44" customWidth="1"/><col min="4" max="${lastCol + 1}" width="17" customWidth="1"/></cols>""")
        sb.append("<sheetData>")

        // Fila 1: título (nombre del negocio) combinado
        sb.append("""<row r="1" ht="48" customHeight="1">""")
        sb.append("""<c r="${col(first)}1" s="$S_TITLE" t="inlineStr"><is><t>${esc(businessName.trim().uppercase())}</t></is></c>""")
        for (c in first + 1..lastCol) sb.append("""<c r="${col(c)}1" s="$S_TITLE"/>""")
        sb.append("</row>")

        // Fila 2: encabezados
        sb.append("""<row r="$headRow" ht="22" customHeight="1">""")
        sb.append("""<c r="B$headRow" s="$S_HEAD" t="inlineStr"><is><t>FECHA</t></is></c>""")
        sb.append("""<c r="C$headRow" s="$S_HEAD" t="inlineStr"><is><t>DESCRIPCION</t></is></c>""")
        cols.forEachIndexed { i, n ->
            sb.append("""<c r="${col(3 + i)}$headRow" s="$S_HEAD" t="inlineStr"><is><t>${esc(n)}</t></is></c>""")
        }
        sb.append("</row>")

        // Datos
        rows.forEachIndexed { idx, r ->
            val rn = firstData + idx
            val target = cols.indexOf(r.seller.trim().uppercase().ifBlank { NO_SELLER })
            sb.append("""<row r="$rn">""")
            sb.append("""<c r="B$rn" s="$S_DATE"><v>${serial(r.dateMillis)}</v></c>""")
            sb.append("""<c r="C$rn" s="$S_TEXT" t="inlineStr"><is><t>${esc(r.description)}</t></is></c>""")
            cols.indices.forEach { i ->
                if (i == target) sb.append("""<c r="${col(3 + i)}$rn" s="$S_MONEY"><v>${r.amount}</v></c>""")
                else sb.append("""<c r="${col(3 + i)}$rn" s="$S_MONEY"/>""")
            }
            sb.append("</row>")
        }

        // Totales por trabajador
        sb.append("""<row r="$totalRow">""")
        sb.append("""<c r="B$totalRow" s="$S_TOT_LABEL"/>""")
        sb.append("""<c r="C$totalRow" s="$S_TOT_LABEL" t="inlineStr"><is><t>TOTAL</t></is></c>""")
        cols.forEachIndexed { i, n ->
            val letter = col(3 + i)
            val sum = rows.filter { (it.seller.trim().uppercase().ifBlank { NO_SELLER }) == n }.sumOf { it.amount }
            if (rows.isEmpty()) sb.append("""<c r="$letter$totalRow" s="$S_TOT_MONEY"><v>0</v></c>""")
            else sb.append("""<c r="$letter$totalRow" s="$S_TOT_MONEY"><f>SUM($letter$firstData:$letter$lastData)</f><v>$sum</v></c>""")
        }
        sb.append("</row>")
        sb.append("</sheetData>")
        sb.append("""<mergeCells count="1"><mergeCell ref="B1:${col(lastCol)}1"/></mergeCells>""")
        sb.append("""<pageMargins left="0.5" right="0.5" top="0.6" bottom="0.6" header="0.3" footer="0.3"/>""")
        sb.append("""<pageSetup orientation="landscape"/>""")
        sb.append("</worksheet>")

        val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="2">
<numFmt numFmtId="164" formatCode="d/m/yyyy"/>
<numFmt numFmtId="165" formatCode="_(&quot;$&quot;* #,##0_);_(&quot;$&quot;* \(#,##0\);_(&quot;$&quot;* &quot;-&quot;??_);_(@_)"/>
</numFmts>
<fonts count="4">
<font><sz val="10"/><name val="Arial"/></font>
<font><b/><sz val="30"/><name val="Arial"/></font>
<font><b/><sz val="11"/><name val="Arial"/></font>
<font><b/><sz val="10"/><name val="Arial"/></font>
</fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color auto="1"/></left><right style="thin"><color auto="1"/></right><top style="thin"><color auto="1"/></top><bottom style="thin"><color auto="1"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="8">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center"/></xf>
<xf numFmtId="165" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>
<xf numFmtId="0" fontId="3" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center"/></xf>
<xf numFmtId="165" fontId="3" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, text: String) {
                z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="VENTAS" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/styles.xml", styles)
            put("xl/worksheets/sheet1.xml", sb.toString())
        }
        return out.toByteArray()
    }

    /** Guarda en la carpeta Descargas. Devuelve el nombre final del archivo. */
    fun saveToDownloads(ctx: Context, fileName: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, MIME)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("no se pudo crear el archivo")
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no se pudo escribir")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            ctx.contentResolver.update(uri, values, null, null)
            return fileName
        }
        // Android 8/9: carpeta Descargas propia de la app (no requiere permisos)
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
        dir.mkdirs()
        File(dir, fileName).writeBytes(bytes)
        return "$fileName (Android/data/${ctx.packageName}/files/Download)"
    }
}
