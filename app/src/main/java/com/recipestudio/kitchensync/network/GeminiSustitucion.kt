package com.recipestudio.kitchensync.network

import android.util.Base64
import com.recipestudio.kitchensync.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class IaException(mensaje: String) : Exception(mensaje)

data class AnalisisIa(
    val producto: String,
    val sirve: Boolean,
    val impactoSaborPct: Int,
    val impactoTexturaPct: Int,
    val cantidadSugerida: String,
    val nota: String
)

/**
 * Manda la foto del producto que SÍ tienes a Gemini y le pide estimar cuánto cambiaría el sabor y la
 * textura al usarlo en lugar del ingrediente que falta. La clave está en local.properties (GEMINI_API_KEY).
 */
object GeminiSustitucion {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    val configurado get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    suspend fun analizar(faltante: String, cantidad: String, fotoJpeg: ByteArray): AnalisisIa = withContext(Dispatchers.IO) {
        if (!configurado) throw IaException("Falta la clave de IA: agrega GEMINI_API_KEY en local.properties y vuelve a compilar")
        val prompt = """
            Eres un chef experto. Una persona cocina y NO tiene este ingrediente: "$faltante"${if (cantidad.isNotBlank()) " (la receta pide $cantidad)" else ""}.
            La foto muestra el producto que SÍ tiene para usar en su lugar.
            1) Identifica el producto de la foto. 2) Estima de 0 a 100 cuánto cambiaría el SABOR y la TEXTURA del platillo
            (0 = idéntico, 100 = totalmente distinto). 3) Di si sirve como sustituto y qué cantidad usar.
            Si la foto no muestra un alimento reconocible, pon "sirve": false y explica en "nota".
            Responde SOLO con JSON con estas claves: producto (string), sirve (boolean), impactoSaborPct (entero),
            impactoTexturaPct (entero), cantidadSugerida (string corto), nota (máximo 200 caracteres, en español).
        """.trimIndent()

        val cuerpo = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text", prompt))
                .put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", "image/jpeg")
                    .put("data", Base64.encodeToString(fotoJpeg, Base64.NO_WRAP)))))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))

        val conn = URL(BASE + BuildConfig.GEMINI_MODEL + ":generateContent").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 45000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
            conn.outputStream.use { it.write(cuerpo.toString().toByteArray(Charsets.UTF_8)) }
            val codigo = conn.responseCode
            val texto = (if (codigo in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (codigo !in 200..299) {
                throw IaException(
                    when (codigo) {
                        400 -> "La IA rechazó la petición (400). Revisa el nombre del modelo en local.properties (GEMINI_MODEL)"
                        401, 403 -> "La clave de IA no es válida o no tiene permiso (HTTP $codigo)"
                        404 -> "No existe el modelo de IA configurado (GEMINI_MODEL)"
                        429 -> "Se acabó el cupo gratuito de la IA por ahora, intenta en un minuto"
                        else -> "La IA no respondió bien (HTTP $codigo)"
                    }
                )
            }
            parsear(texto)
        } catch (e: IOException) {
            throw IaException("No se pudo conectar con la IA. Revisa tu internet")
        } finally {
            conn.disconnect()
        }
    }

    private fun parsear(respuesta: String): AnalisisIa {
        try {
            val texto = JSONObject(respuesta).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                .trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val o = JSONObject(texto)
            return AnalisisIa(
                producto = o.optString("producto", "No identificado").take(60),
                sirve = o.optBoolean("sirve", false),
                impactoSaborPct = o.optInt("impactoSaborPct", 0).coerceIn(0, 100),
                impactoTexturaPct = o.optInt("impactoTexturaPct", 0).coerceIn(0, 100),
                cantidadSugerida = o.optString("cantidadSugerida", "").take(60),
                nota = o.optString("nota", "").take(240)
            )
        } catch (e: Exception) {
            throw IaException("No pude entender la respuesta de la IA, intenta con otra foto")
        }
    }
}
