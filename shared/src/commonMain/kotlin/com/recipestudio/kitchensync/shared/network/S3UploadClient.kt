package com.recipestudio.kitchensync.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

class S3Exception(mensaje: String, val codigo: Int = 0) : Exception(mensaje) {
    /** 503 = el servidor no tiene S3 configurado: la app usa su respaldo en vez de mostrar un error. */
    val noConfigurado get() = codigo == 503
}

/**
 * Subida de fotos con URL prefirmada (estrategia de multimedia del proyecto), código compartido KMM con Ktor:
 *   Paso 1: GET {servidor}/api/upload-url  -> el servidor firma una URL PUT que caduca en 5 minutos.
 *   Paso 2: PUT de la imagen directo a S3 con esa URL (el servidor nunca recibe los bytes de la foto).
 * Devuelve la CLAVE del objeto (p. ej. fotos/20261114/ab12...jpg), que se guarda en Firestore.
 */
class S3UploadClient(
    baseUrl: String,
    private val sessionId: String,
    private val token: String,
    private val http: HttpClient = HttpClient()
) {
    private val base = baseUrl.trimEnd('/')

    suspend fun subirFoto(bytes: ByteArray, contentType: String = "image/jpeg"): String {
        require(bytes.isNotEmpty()) { "La imagen está vacía" }
        require(bytes.size <= MAX_BYTES) { "La imagen supera ${MAX_BYTES / 1_000_000} MB" }
        val firmada = pedirUrl("/api/upload-url") {
            parameter("sessionId", sessionId); parameter("token", token); parameter("contentType", contentType)
        }
        val url = extraerCampo(firmada, "url") ?: throw S3Exception("Respuesta inválida del servidor")
        val clave = extraerCampo(firmada, "key") ?: throw S3Exception("Respuesta inválida del servidor")
        try {
            val put = http.put(url) {
                header(HttpHeaders.ContentType, contentType)     // va FIRMADO: debe coincidir con la URL
                setBody(bytes)
            }
            if (!put.status.isSuccess()) throw S3Exception("S3 rechazó la subida (${put.status.value})", put.status.value)
        } catch (e: S3Exception) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw S3Exception("No se pudo subir la foto. Revisa tu internet")
        }
        return clave
    }

    /** URL temporal (1 h) para mostrar una foto privada guardada en S3. */
    suspend fun urlDescarga(clave: String): String {
        val r = pedirUrl("/api/download-url") { parameter("sessionId", sessionId); parameter("token", token); parameter("key", clave) }
        return extraerCampo(r, "url") ?: throw S3Exception("Respuesta inválida del servidor")
    }

    private suspend fun pedirUrl(ruta: String, config: io.ktor.client.request.HttpRequestBuilder.() -> Unit): String {
        try {
            val r = http.get(base + ruta) { config() }
            val texto = r.bodyAsText()
            if (!r.status.isSuccess()) {
                throw S3Exception(extraerCampo(texto, "error") ?: "El servidor respondió ${r.status.value}", r.status.value)
            }
            return texto
        } catch (e: S3Exception) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw S3Exception("No se pudo contactar al servidor")
        }
    }

    companion object {
        const val MAX_BYTES = 5_000_000
    }
}

/** Lee un campo de texto de un JSON plano ({"campo": "valor"}) sin depender de librerías de JSON. */
internal fun extraerCampo(json: String, campo: String): String? {
    val m = Regex("\"" + Regex.escape(campo) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(json) ?: return null
    return m.groupValues[1].replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\").replace("\\u0026", "&")
}
