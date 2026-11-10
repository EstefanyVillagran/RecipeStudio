package com.recipestudio.kitchensync.network

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** Sin consultas DNS (en el hilo principal de Android lanzan error): solo se reconocen direcciones y nombres locales. */
private fun esHostPrivadoOlocal(host: String): Boolean {
    val h = host.lowercase().trim().removePrefix("[").removeSuffix("]")
    if (h.isBlank() || h == "localhost" || h.endsWith(".local") || h.endsWith(".localhost")) return true
    val partes = h.split('.')
    if (partes.size == 4 && partes.all { p -> p.toIntOrNull()?.let { n -> n in 0..255 } == true }) {
        val a = partes[0].toInt()
        val b = partes[1].toInt()
        return a == 0 || a == 10 || a == 127 || (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }
    return h.contains(':') && (h == "::1" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd"))
}

/** Dirección de la estación y sesión del QR guardadas en el celular (las escribe RecipeStudioScreen). */
object ServerConfig {
    private const val PREFS = "kitchen_session_state"
    private const val KEY = "server_base_url"
    const val DEFAULT = "https://recipestudio.onrender.com" // backend publicado en Render

    fun normalizar(url: String?): String {
        val valor = url?.trim()?.trimEnd('/') ?: DEFAULT
        if (valor.isBlank()) return DEFAULT

        return try {
            val uri = java.net.URI(valor)
            val host = uri.host ?: valor.substringAfter("//").substringBefore('/').substringBefore(':')
            if (esHostPrivadoOlocal(host)) DEFAULT else valor
        } catch (_: Exception) {
            val host = valor.substringAfter("//").substringBefore('/').substringBefore(':').lowercase()
            if (esHostPrivadoOlocal(host)) DEFAULT else valor
        }
    }

    fun leer(context: Context): String =
        normalizar(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, DEFAULT))

    fun guardar(context: Context, url: String) {
        val limpio = normalizar(url)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, limpio).apply()
    }

    /** Sesión vinculada por QR, o null si el celular todavía no escaneó el código. */
    fun sesion(context: Context): SesionQr? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = p.getString("session_id", null)
        val token = p.getString("session_token", null)
        if (!p.getBoolean("vinculado", false) || id.isNullOrBlank() || token.isNullOrBlank()) return null
        return SesionQr(id, token, nombreDispositivo())
    }

    /** Nombre visible en el chat y reportes (el servidor lo vuelve a validar). */
    fun nombreDispositivo(): String =
        Build.MODEL.orEmpty().filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '.' }.trim().take(30).ifBlank { "Celular" }
}

/**
 * Encuentra la estación en la misma red sin escribir la IP: manda un aviso UDP a toda la red
 * y el servidor contesta con su dirección actual.
 */
object ServerDiscovery {
    private const val PORT = 48555
    private val PREGUNTA = "KITCHENSYNC?".toByteArray()

    suspend fun buscar(): String? = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 1200
                // red local + alias del emulador hacia la PC anfitriona
                listOf("255.255.255.255", "10.0.2.2").forEach { destino ->
                    try {
                        socket.send(DatagramPacket(PREGUNTA, PREGUNTA.size, InetAddress.getByName(destino), PORT))
                    } catch (_: Exception) { }
                }
                val buffer = ByteArray(256)
                val respuesta = DatagramPacket(buffer, buffer.size)
                socket.receive(respuesta)
                val texto = String(respuesta.data, 0, respuesta.length).trim()
                val destino = if (texto.startsWith("KITCHENSYNC|")) texto.removePrefix("KITCHENSYNC|").trim() else null
                destino?.let { ServerConfig.normalizar(it) }?.takeIf { it != ServerConfig.DEFAULT || destino.contains("https://") }
            }
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
