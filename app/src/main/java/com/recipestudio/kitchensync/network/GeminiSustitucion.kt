package com.recipestudio.kitchensync.network

import android.util.Base64
import com.recipestudio.kitchensync.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

open class IaException(mensaje: String) : Exception(mensaje)
private class IaHttp(val codigo: Int, mensaje: String) : IaException(mensaje)

data class AnalisisIa(
    val producto: String,
    val sirve: Boolean,
    val impactoSaborPct: Int,
    val impactoTexturaPct: Int,
    val cantidadSugerida: String,
    val nota: String,
    val confianza: String = "media",              // alta / media / baja
    val consejo: String = "",                     // cómo usarlo para que se note menos
    val alternativas: List<String> = emptyList()  // otras opciones comunes
)

/**
 * Manda la foto del producto que SÍ tienes a Gemini y le pide estimar cuánto cambiaría el sabor y la textura al
 * usarlo en lugar del ingrediente que falta. La clave está en local.properties (GEMINI_API_KEY).
 *
 * Robustez: respuesta con esquema JSON, reintentos con espera si hay saturación (429/5xx), y si el modelo configurado
 * no existe o rechaza el esquema, prueba con el siguiente modelo.
 */
object GeminiSustitucion {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    val configurado get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    private fun modelos(): List<String> = listOf(BuildConfig.GEMINI_MODEL, "gemini-flash-latest", "gemini-2.5-flash").filter { it.isNotBlank() }.distinct()

    suspend fun analizar(faltante: String, cantidad: String, fotoJpeg: ByteArray, receta: String = ""): AnalisisIa = withContext(Dispatchers.IO) {
        if (!configurado) throw IaException("La IA no está activada en esta versión de la app (falta GEMINI_API_KEY)")
        var ultimo: IaHttp? = null
        for (modelo in modelos()) {
            var conEsquema = true
            var intento = 0
            while (true) {
                try {
                    return@withContext llamar(modelo, armarCuerpo(faltante, cantidad, receta, fotoJpeg, conEsquema))
                } catch (e: IaHttp) {
                    when {
                        e.codigo == 400 && conEsquema -> conEsquema = false                 // por si no acepta el esquema
                        (e.codigo == 429 || e.codigo >= 500) && intento < 2 -> { intento++; delay(2000L * intento) }
                        e.codigo == 400 || e.codigo == 404 -> { ultimo = e; break }          // probar el siguiente modelo
                        else -> throw e
                    }
                }
            }
        }
        throw ultimo ?: IaException("No se pudo usar la IA, intenta más tarde")
    }

    private fun armarCuerpo(faltante: String, cantidad: String, receta: String, foto: ByteArray, conEsquema: Boolean): JSONObject {
        val prompt = """
            Eres un chef experto en cocina mexicana y latinoamericana que ayuda a sustituir ingredientes en casa.
            ${if (receta.isNotBlank()) "Receta: \"$receta\"." else ""}
            La persona NO tiene este ingrediente: "$faltante"${if (cantidad.isNotBlank()) " (la receta pide $cantidad)" else ""}.
            La foto muestra lo que SÍ tiene para usar en su lugar. Si hay varios productos, analiza el que más se ve.
            Tareas:
            1) Identifica el producto de la foto (nombre común en español).
            2) Estima de 0 a 100 cuánto cambiaría el SABOR y la TEXTURA del platillo (0 = idéntico, 100 = totalmente distinto).
               Sé realista y consistente: un cambio pequeño es menos de 15, moderado de 15 a 25 y alto más de 25.
            3) Di si sirve como sustituto y qué cantidad usar respecto a la receta (ej. "1 taza", "3/4 de la cantidad").
            4) Da un consejo corto para que el cambio se note menos y hasta 2 alternativas comunes.
            5) Indica tu confianza: "alta" si el producto se ve claro, "media" o "baja" si la foto es borrosa o ambigua.
            Si la foto no muestra un alimento o ingrediente reconocible, pon sirve=false, confianza="baja" y explica en nota.
            Responde SOLO JSON. Todo el texto en español, claro y breve (nota máximo 200 caracteres).
        """.trimIndent()

        val generacion = JSONObject().put("responseMimeType", "application/json")
        if (conEsquema) generacion.put("responseSchema", JSONObject()
            .put("type", "OBJECT")
            .put("properties", JSONObject()
                .put("producto", JSONObject().put("type", "STRING"))
                .put("sirve", JSONObject().put("type", "BOOLEAN"))
                .put("impactoSaborPct", JSONObject().put("type", "INTEGER"))
                .put("impactoTexturaPct", JSONObject().put("type", "INTEGER"))
                .put("cantidadSugerida", JSONObject().put("type", "STRING"))
                .put("nota", JSONObject().put("type", "STRING"))
                .put("confianza", JSONObject().put("type", "STRING"))
                .put("consejo", JSONObject().put("type", "STRING"))
                .put("alternativas", JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING"))))
            .put("required", JSONArray(listOf("producto", "sirve", "impactoSaborPct", "impactoTexturaPct", "cantidadSugerida", "nota"))))

        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text", prompt))
                .put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", "image/jpeg")
                    .put("data", Base64.encodeToString(foto, Base64.NO_WRAP)))))))
            .put("generationConfig", generacion)
    }

    private fun llamar(modelo: String, cuerpo: JSONObject): AnalisisIa {
        val conn = URL(BASE + modelo + ":generateContent").openConnection() as HttpURLConnection
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
                throw IaHttp(
                    codigo,
                    when (codigo) {
                        400 -> "La IA rechazó la petición (400)"
                        402 -> "Tu clave de Gemini está en un proyecto con facturación sin saldo. Crea una clave nueva en un proyecto SIN facturación (aistudio.google.com/apikey) para usar el plan gratuito"
                        401, 403 -> "La clave de IA no es válida o no tiene permiso (HTTP $codigo)"
                        404 -> "No existe el modelo de IA configurado"
                        429 -> "La IA está saturada o se acabó el cupo gratuito por ahora. Intenta en un minuto"
                        else -> "La IA no respondió bien (HTTP $codigo)"
                    }
                )
            }
            return parsear(texto)
        } catch (e: IOException) {
            throw IaException("No se pudo conectar con la IA. Revisa tu internet")
        } finally {
            conn.disconnect()
        }
    }

    internal fun parsear(respuesta: String): AnalisisIa {
        try {
            val raiz = JSONObject(respuesta)
            val candidatos = raiz.optJSONArray("candidates")
            if (candidatos == null || candidatos.length() == 0) {
                throw IaException("La IA no pudo analizar esa imagen. Prueba con otra foto más clara")
            }
            val texto = candidatos.getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            val json = texto.substring(texto.indexOf('{'), texto.lastIndexOf('}') + 1)   // tolera ```json ... ```
            val o = JSONObject(json)
            val alternativas = o.optJSONArray("alternativas")
            return AnalisisIa(
                producto = o.optString("producto", "No identificado").take(60),
                sirve = o.optBoolean("sirve", false),
                impactoSaborPct = o.optInt("impactoSaborPct", 0).coerceIn(0, 100),
                impactoTexturaPct = o.optInt("impactoTexturaPct", 0).coerceIn(0, 100),
                cantidadSugerida = o.optString("cantidadSugerida", "").take(60),
                nota = o.optString("nota", "").take(240),
                confianza = o.optString("confianza", "media").lowercase().let { if (it in listOf("alta", "media", "baja")) it else "media" },
                consejo = o.optString("consejo", "").take(200),
                alternativas = (0 until (alternativas?.length() ?: 0)).map { alternativas!!.optString(it).take(40) }.filter { it.isNotBlank() }.take(2)
            )
        } catch (e: IaException) {
            throw e
        } catch (e: Exception) {
            throw IaException("No pude entender la respuesta de la IA, intenta con otra foto")
        }
    }
}
