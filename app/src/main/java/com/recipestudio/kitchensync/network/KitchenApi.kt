package com.recipestudio.kitchensync.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiException(val status: Int, mensaje: String) : Exception(mensaje)

data class MensajeChat(val emisor: String, val rol: String, val texto: String, val hora: String)
data class ChatSnapshot(val mensajes: List<MensajeChat>, val contadores: Map<String, Int>)
data class NotaReporte(val autor: String, val texto: String, val hora: String)
data class Reporte(
    val id: String,
    val titulo: String,
    val descripcion: String,
    val categoria: String,
    val prioridad: String,
    val estado: String,
    val nombre: String,
    val asignadoA: String?,
    val notas: List<NotaReporte>
)

/** Datos de la sesión vinculada por QR. El celular no tiene cuenta: el QR es su identidad. */
data class SesionQr(val sessionId: String, val token: String, val dispositivo: String)

/**
 * Cliente HTTP del servidor KitchenSync para el celular. Se identifica con la sesión del QR.
 * Nunca registra tokens ni cuerpos de petición. El nombre que ven los demás lo valida el servidor.
 */
class KitchenApi(private val baseUrl: String, private val sesion: SesionQr) {

    suspend fun leerChat(): ChatSnapshot = parseChat(call("GET", "/chat" + consulta()))

    suspend fun enviarChat(texto: String): ChatSnapshot {
        call("POST", "/chat", conSesion(JSONObject().put("texto", texto.trim())))
        return leerChat()
    }

    suspend fun reportar(titulo: String, descripcion: String, categoria: String, prioridad: String): Reporte {
        val body = conSesion(
            JSONObject().put("titulo", titulo.trim()).put("descripcion", descripcion.trim())
                .put("categoria", categoria).put("prioridad", prioridad)
        )
        return parseReporte(call("POST", "/reportar", body).getJSONObject("reporte"))
    }

    suspend fun misReportes(): List<Reporte> = parseReportes(call("GET", "/mis_reportes" + consulta()))

    private fun conSesion(o: JSONObject): JSONObject =
        o.put("sessionId", sesion.sessionId).put("token", sesion.token).put("dispositivo", sesion.dispositivo)

    private fun consulta(): String = "?sessionId=" + URLEncoder.encode(sesion.sessionId, "UTF-8") +
        "&token=" + URLEncoder.encode(sesion.token, "UTF-8") +
        "&dispositivo=" + URLEncoder.encode(sesion.dispositivo, "UTF-8")

    // ---------- red ----------
    private suspend fun call(metodo: String, ruta: String, cuerpo: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(baseUrl.trimEnd('/') + ruta).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = metodo
                conn.connectTimeout = 15000   // Render gratis tarda en despertar
                conn.readTimeout = 15000
                conn.setRequestProperty("Accept", "application/json")
                if (cuerpo != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(cuerpo.toString().toByteArray(Charsets.UTF_8)) }
                }
                val codigo = conn.responseCode
                val stream = if (codigo in 200..299) conn.inputStream else conn.errorStream
                val texto = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = try { JSONObject(texto) } catch (e: Exception) { JSONObject() }
                if (codigo !in 200..299) {
                    throw ApiException(codigo, json.optString("error", mensajePorCodigo(codigo)))
                }
                json
            } catch (e: IOException) {
                throw ApiException(0, "No se pudo conectar con la estación")
            } finally {
                conn.disconnect()
            }
        }

    private fun mensajePorCodigo(codigo: Int) = when (codigo) {
        401 -> "Vincula tu celular con el QR"
        403 -> "No tienes permiso para esta operación"
        else -> "Error del servidor ($codigo)"
    }

    private fun parseChat(r: JSONObject): ChatSnapshot {
        val arr = r.optJSONArray("mensajes")
        val mensajes = (0 until (arr?.length() ?: 0)).map {
            val m = arr!!.getJSONObject(it)
            MensajeChat(m.getString("emisor"), m.optString("rol"), m.getString("texto"), m.getString("hora"))
        }
        val cont = mutableMapOf<String, Int>()
        r.optJSONObject("contadores")?.let { o -> o.keys().forEach { k -> cont[k] = o.getInt(k) } }
        return ChatSnapshot(mensajes, cont)
    }

    private fun parseReportes(r: JSONObject): List<Reporte> {
        val arr = r.optJSONArray("reportes")
        return (0 until (arr?.length() ?: 0)).map { parseReporte(arr!!.getJSONObject(it)) }
    }

    private fun parseReporte(o: JSONObject): Reporte {
        val n = o.optJSONArray("notas")
        val notas = (0 until (n?.length() ?: 0)).map {
            val x = n!!.getJSONObject(it)
            NotaReporte(x.getString("autor"), x.getString("texto"), x.getString("hora"))
        }
        return Reporte(
            o.getString("id"), o.getString("titulo"), o.getString("descripcion"), o.getString("categoria"),
            o.getString("prioridad"), o.getString("estado"), o.optString("nombre"),
            if (o.isNull("asignadoA")) null else o.getString("asignadoA"), notas
        )
    }
}
