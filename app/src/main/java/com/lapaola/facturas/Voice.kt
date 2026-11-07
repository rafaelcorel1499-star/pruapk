package com.lapaola.facturas

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.text.Normalizer

/** Dictado por voz con el reconocedor de Android (español Colombia). */
class Dictation(
    private val ctx: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onFail: (String) -> Unit,
    private val onState: (Boolean) -> Unit
) {
    private var rec: SpeechRecognizer? = null

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onFail("Este celular no tiene reconocimiento de voz (instala/activa Google)")
            return
        }
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onState(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                onState(false)
                onFail(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No te escuché, intenta de nuevo"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sin internet para el dictado"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta permiso de micrófono"
                        else -> "Error de voz ($error)"
                    }
                )
            }
            override fun onResults(results: Bundle?) {
                onState(false)
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) onFail("No te escuché, intenta de nuevo") else onFinal(t)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { onPartial(it) }
            }
        })
        rec = r
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }

    fun stop() {
        rec?.destroy()
        rec = null
        onState(false)
    }
}

// ---------- Interpretación del texto dictado ----------

private fun strip(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

private val NUM = mapOf(
    "cero" to 0, "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
    "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
    "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15,
    "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19,
    "veinte" to 20, "veintiun" to 21, "veintiuno" to 21, "veintidos" to 22, "veintitres" to 23,
    "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26, "veintisiete" to 27,
    "veintiocho" to 28, "veintinueve" to 29, "treinta" to 30, "cuarenta" to 40,
    "cincuenta" to 50, "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90,
    "cien" to 100, "ciento" to 100, "doscientos" to 200, "trescientos" to 300,
    "cuatrocientos" to 400, "quinientos" to 500, "seiscientos" to 600, "setecientos" to 700,
    "ochocientos" to 800, "novecientos" to 900
)

private val DIGITS = Regex("[\\d.,]+")
private fun isDigitToken(t: String) = DIGITS.matches(t) && t.any { it.isDigit() }
private fun isNumToken(t: String) = isDigitToken(t) || NUM.containsKey(t) || t == "mil" || t == "millon" || t == "millones"

/** "2.500", "2500", "dos mil quinientos", "2 mil 500", "600 pesos" -> número. */
fun wordsToNumber(text: String): Long? {
    var total = 0L
    var cur = 0L
    var seen = false
    var prevDigits = false
    for (tok in strip(text.lowercase()).split(Regex("\\s+")).filter { it.isNotEmpty() }) {
        var digits = false
        when {
            isDigitToken(tok) -> {
                val v = tok.filter { it.isDigit() }.toLongOrNull() ?: 0L
                cur = if (prevDigits && tok.length == 3 && cur in 1..999) cur * 1000 + v else cur + v
                seen = true
                digits = true
            }
            NUM.containsKey(tok) -> { cur += NUM[tok]!!; seen = true }
            tok == "mil" -> { total += (if (cur == 0L) 1L else cur) * 1000L; cur = 0L; seen = true }
            tok == "millon" || tok == "millones" -> { total += (if (cur == 0L) 1L else cur) * 1_000_000L; cur = 0L; seen = true }
        }
        prevDigits = digits
    }
    return if (seen) total + cur else null
}

private val SMALL = setOf("de", "del", "la", "el", "con", "y", "en", "al", "a", "para")
private fun titleCase(s: String): String =
    s.split(" ").filter { it.isNotEmpty() }.mapIndexed { i, w ->
        if (i > 0 && w.lowercase() in SMALL) w.lowercase()
        else w.lowercase().replaceFirstChar { it.uppercase() }
    }.joinToString(" ")

private val PRICE_KEYS = setOf("valor", "vale", "valen", "cuesta", "cuestan", "precio", "por")

/**
 * Ejemplos: "4 panes de piña valor 2.500 pesos" -> 4 x Pan de Piña @2500
 *           "banano maduro valor 600 pesos"      -> 1 x Banano Maduro @600
 * El valor dictado es el precio por unidad.
 */
fun parseDictation(raw: String): CartLine? {
    val tokens = raw.replace("\$", " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.size < 2) return null
    val low = tokens.map { strip(it.lowercase()) }

    var nameEnd = -1
    var priceStart = -1
    val k = low.indexOfLast { it in PRICE_KEYS }
    if (k >= 1 && k < tokens.size - 1) {
        nameEnd = k
        priceStart = if (low[k + 1] == "de" && k + 2 < tokens.size) k + 2 else k + 1
    } else {
        var i = tokens.size
        while (i > 0 && (isNumToken(low[i - 1]) || low[i - 1] in setOf("pesos", "peso", "cop"))) i--
        if (i in 1 until tokens.size && low.subList(i, low.size).any { isNumToken(it) }) {
            nameEnd = i
            priceStart = i
        }
    }
    if (nameEnd < 1) return null

    val price = wordsToNumber(tokens.subList(priceStart, tokens.size).joinToString(" ")) ?: return null
    if (price <= 0) return null

    var nameTokens = tokens.subList(0, nameEnd)
    var nameLow = low.subList(0, nameEnd)
    var qty = 1
    if (nameTokens.size > 1) {
        val f = nameLow[0]
        val q = if (isDigitToken(f)) f.filter { it.isDigit() }.toIntOrNull() else NUM[f]
        if (q != null && q in 1..999) {
            qty = q
            nameTokens = nameTokens.drop(1)
            nameLow = nameLow.drop(1)
        }
    }
    if (nameTokens.isEmpty()) return null
    var name = nameTokens.toMutableList()
    if (qty > 1 && nameLow[0] == "panes") name[0] = "pan"
    return CartLine(titleCase(name.joinToString(" ")), qty, price)
}
