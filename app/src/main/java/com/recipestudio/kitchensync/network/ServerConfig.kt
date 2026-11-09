package com.recipestudio.kitchensync.network

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** Dirección de la estación y sesión del QR guardadas en el celular (las escribe RecipeStudioScreen). */
object ServerConfig {
    private const val PREFS = "kitchen_session_state"
    private const val KEY = "server_base_url"
    const val DEFAULT = "https://recipestudio.onrender.com" // backend publicado en Render

    fun normalizar(url: String?): String {
        val valor = url?.trim()?.trimEnd('/') ?: DEFAULT
        if (valor.isBlank()) return DEFAULT

        val host = valor.lowercase()
        val esLocalOPrivada = host.startsWith("http://10.") ||
            host.startsWith("http://192.168.") ||
            host.startsWith("http://127.0.0.1") ||
            host.startsWith("http://localhost") ||
            host.startsWith("http://0.0.0.0") ||
            host.contains("10.0.2.2") ||
            host.contains("10.30.77.8")

        return if (esLocalOPrivada) DEFAULT else valor
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
                val texto = String(respuesta.data, 0, respuesta.length)
                if (texto.startsWith("KITCHENSYNC|")) texto.removePrefix("KITCHENSYNC|").trim() else null
            }
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
