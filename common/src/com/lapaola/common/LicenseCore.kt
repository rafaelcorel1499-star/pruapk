package com.lapaola.common

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Lógica de llaves de uso. Este archivo lo comparten la app de facturación y el generador de llaves.
 *
 * Formato de la llave: 24 caracteres (6 grupos de 4) = 15 bytes:
 *   4 bytes aleatorios + 4 bytes de código de dispositivo (ceros = llave general) + 7 bytes de firma HMAC.
 *
 * IMPORTANTE: si cambias SECRET_PARTS, todas las llaves anteriores dejan de servir.
 * Cámbialo UNA vez, antes de repartir la app, y mantén privada la APK del generador.
 */
object LicenseCore {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private val SECRET_PARTS = listOf("a566d8adc2d681fe", "d611626a860b349a", "2c6b866d3a18ed73", "2d3928d8912ead50")

    private fun secret(): ByteArray =
        SECRET_PARTS.joinToString("").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun hmac(data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret(), "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    private fun encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    private fun decode(s: String): ByteArray? {
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in s) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        return out.toByteArray()
    }

    private fun deviceTag(deviceId: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(("lapaola-dev:" + deviceId).toByteArray()).copyOf(4)

    /** Código corto (8 caracteres hexadecimales) que muestra la app para pedir una llave exclusiva. */
    fun deviceCode(deviceId: String): String = hex(deviceTag(deviceId))

    /** Quita guiones/espacios y pasa a mayúsculas. */
    fun normalize(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }

    fun format(key: String): String = key.chunked(4).joinToString("-")

    fun isValidDeviceCode(s: String): Boolean =
        s.trim().length == 8 && s.trim().all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }

    /** Genera una llave. [deviceCodeHex] = código de 8 hex del dispositivo, o null/vacío para una llave general. */
    fun generate(deviceCodeHex: String?): String {
        val payload = ByteArray(8)
        SecureRandom().nextBytes(payload)
        val tag = if (deviceCodeHex.isNullOrBlank()) ByteArray(4)
        else deviceCodeHex.trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        System.arraycopy(tag, 0, payload, 4, 4)
        val sig = hmac(payload).copyOf(7)
        return format(encode(payload + sig))
    }

    fun verify(key: String, deviceId: String): Boolean {
        val raw = decode(normalize(key)) ?: return false
        if (raw.size != 15) return false
        val payload = raw.copyOfRange(0, 8)
        val sig = raw.copyOfRange(8, 15)
        if (!MessageDigest.isEqual(sig, hmac(payload).copyOf(7))) return false
        val tag = payload.copyOfRange(4, 8)
        val bound = tag.any { it.toInt() != 0 }
        return !bound || MessageDigest.isEqual(tag, deviceTag(deviceId))
    }

    /** Sello que vincula la llave con este dispositivo (se guarda al activar). */
    fun token(key: String, deviceId: String): String =
        hex(hmac(("act:" + normalize(key) + ":" + deviceId).toByteArray()))
}
