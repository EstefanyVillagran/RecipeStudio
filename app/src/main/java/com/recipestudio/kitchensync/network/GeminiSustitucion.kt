package com.recipestudio.kitchensync.network

import android.util.Base64
import com.recipestudio.kitchensync.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

open class IaException(mensaje: String) : Exception(mensaje)
private class IaHttp(val codigo: Int, mensaje: String) : IaException(mensaje)
private class IaRed(mensaje: String) : IaException(mensaje)

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
 * Robustez: esquema JSON, reintentos con espera (saturación y fallas de red pasajeras), y si el modelo configurado no existe
 * pregunta a Google qué modelos tiene la clave y usa uno de ellos. Los errores dicen la causa real (incluido el mensaje de Google).
 */
object GeminiSustitucion {
    private const val RAIZ = "https://generativelanguage.googleapis.com/v1beta/"

    val configurado get() = BuildConfig.GEMINI_API_KEY.isNotBlank()
    private fun clave() = BuildConfig.GEMINI_API_KEY.trim()

    private fun configurados(): List<String> =
        listOf(BuildConfig.GEMINI_MODEL, "gemini-flash-latest", "gemini-2.5-flash", "gemini-2.0-flash").map { it.trim() }.filter { it.isNotBlank() }.distinct()

    @Volatile private var modeloQueFunciono: String? = null

    suspend fun analizar(faltante: String, cantidad: String, fotoJpeg: ByteArray, receta: String = ""): AnalisisIa = withContext(Dispatchers.IO) {
        if (!configurado) throw IaException("La IA no está activada en esta versión de la app (falta GEMINI_API_KEY)")
        var ultimo: IaException? = null
        var modelos = (listOfNotNull(modeloQueFunciono) + configurados()).distinct()
        var yaDescubrio = false
        var ronda = 0
        var i = 0
        while (true) {
            if (i >= modelos.size) {
                if (!yaDescubrio) {          // ninguno de los nombres conocidos sirvió: se consulta qué modelos tiene esta clave
                    yaDescubrio = true
                    val descubiertos = try { modelosDisponibles().filter { it !in modelos }.take(4) } catch (e: IaException) { emptyList() }
                    modelos = modelos + descubiertos
                    if (i < modelos.size) continue
                }
                // Todos saturados (503/429): se espera un poco y se vuelve a recorrer la lista una vez
                val saturado = (ultimo as? IaHttp)?.let { it.codigo == 429 || it.codigo >= 500 } == true
                if (saturado && ronda < 1) { ronda++; delay(5000L); i = 0; continue }
                break
            }
            val modelo = modelos[i]
            var conEsquema = true
            var intentos = 0
            var siguienteModelo = false
            while (!siguienteModelo) {
                try {
                    val r = llamar(modelo, armarCuerpo(faltante, cantidad, receta, fotoJpeg, conEsquema))
                    modeloQueFunciono = modelo
                    return@withContext r
                } catch (e: IaHttp) {
                    ultimo = e
                    when {
                        e.codigo == 400 && conEsquema -> conEsquema = false                       // por si no acepta el esquema
                        (e.codigo == 429 || e.codigo >= 500) && intentos < 1 -> { intentos++; delay(2500L) }
                        e.codigo == 400 || e.codigo == 404 || e.codigo == 429 || e.codigo >= 500 -> siguienteModelo = true   // probar OTRO modelo
                        else -> throw e                                                           // 401/402/403: otro modelo no ayuda
                    }
                } catch (e: IaRed) {
                    ultimo = e
                    if (intentos < 2) { intentos++; delay(1500L * intentos) } else throw e
                }
            }
            i++
        }
        throw ultimo ?: IaException("No se pudo usar la IA, intenta más tarde")
    }

    /** Prueba paso a paso (internet, clave, modelos y una petición real) y devuelve un texto legible con la causa de cualquier falla. */
    suspend fun diagnostico(): String = withContext(Dispatchers.IO) {
        if (!configurado) return@withContext "❌ La app se compiló SIN clave. Pon GEMINI_API_KEY=... en local.properties, sincroniza y vuelve a instalar."
        val k = BuildConfig.GEMINI_API_KEY
        val sb = StringBuilder()
        sb.append("Clave: ${k.take(6)}… (${k.length} caracteres)\n")
        if (k != k.trim() || k.contains('"') || k.contains(' ')) sb.append("⚠️ La clave tiene espacios o comillas: cópiala de nuevo sin ellos.\n")
        val modelos: List<String>
        try {
            val (codigo, texto) = pedir("GET", "models?pageSize=200", null)
            if (codigo != 200) {
                sb.append("❌ Google respondió $codigo: ${mensajeDeGoogle(texto)}\n")
                sb.append(pista(codigo))
                return@withContext sb.toString()
            }
            sb.append("✅ Internet y clave válidos.\n")
            modelos = extraerModelos(texto)
            sb.append("Modelos que puedes usar: ${modelos.take(4).joinToString().ifBlank { "ninguno" }}\n")
        } catch (e: IaException) {
            sb.append("❌ ${e.message}")
            return@withContext sb.toString()
        }
        val orden = (listOfNotNull(modeloQueFunciono) + configurados() + modelos).distinct().filter { it in modelos }.take(4)
        if (orden.isEmpty()) { sb.append("❌ Esta clave no tiene modelos con generateContent."); return@withContext sb.toString() }
        var funciono = false
        for (m in orden) {
            try {
                val cuerpo = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Responde solo: OK")))))
                val (codigo, texto) = pedir("POST", "models/$m:generateContent", cuerpo.toString())
                if (codigo == 200) { sb.append("✅ El modelo $m respondió. La IA debe funcionar con fotos."); modeloQueFunciono = m; funciono = true; break }
                sb.append(if (codigo == 503 || codigo == 429) "⏳ $m está saturado ahora (HTTP $codigo); pruebo otro…\n" else "❌ $m respondió $codigo: ${mensajeDeGoogle(texto)}\n")
                if (codigo == 401 || codigo == 402 || codigo == 403) { sb.append(pista(codigo)); break }
            } catch (e: IaException) {
                sb.append("❌ ${e.message}"); break
            }
        }
        if (!funciono && !sb.contains("→")) sb.append("Todos los modelos están saturados en este momento: es temporal, intenta en unos minutos.")
        sb.toString()
    }

    private fun pista(codigo: Int) = when (codigo) {
        400 -> "→ La clave no es válida (cópiala de nuevo en aistudio.google.com/apikey).\n"
        401, 403 -> "→ La clave no tiene permiso: crea una nueva en un proyecto de AI Studio.\n"
        402 -> "→ Ese proyecto tiene facturación sin saldo: usa una clave de un proyecto SIN facturación.\n"
        429 -> "→ Se acabó el cupo gratuito por ahora: espera unos minutos.\n"
        else -> ""
    }

    private suspend fun modelosDisponibles(): List<String> {
        val (codigo, texto) = pedir("GET", "models?pageSize=200", null)
        if (codigo != 200) throw IaHttp(codigo, mensajeDeGoogle(texto))
        return extraerModelos(texto)
    }

    /** Modelos "flash" que generan contenido, del más nuevo al más viejo (sin lite, imagen, voz, tiempo real ni embeddings). */
    internal fun extraerModelos(respuesta: String): List<String> = try {
        val arr = JSONObject(respuesta).optJSONArray("models") ?: JSONArray()
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { m -> (0 until (m.optJSONArray("supportedGenerationMethods")?.length() ?: 0)).any { m.getJSONArray("supportedGenerationMethods").getString(it) == "generateContent" } }
            .map { it.optString("name").removePrefix("models/") }
            .filter { n -> n.startsWith("gemini") && "flash" in n && listOf("lite", "image", "tts", "live", "audio", "embedding", "robotics").none { it in n } }
            .sortedDescending()
    } catch (e: Exception) {
        emptyList()
    }

    private fun mensajeDeGoogle(texto: String): String = try {
        JSONObject(texto).getJSONObject("error").optString("message", "sin detalle").take(220)
    } catch (e: Exception) {
        texto.take(120).ifBlank { "sin detalle" }
    }

    private fun describir(e: IOException): String = when (e) {
        is UnknownHostException -> "No hay internet (o no se puede resolver generativelanguage.googleapis.com)"
        is SocketTimeoutException -> "La IA tardó demasiado en responder. Intenta de nuevo"
        is SSLException -> "Falló la conexión segura: revisa que la fecha y la hora del celular sean correctas"
        is ConnectException -> "No se pudo abrir la conexión con la IA (¿VPN, datos móviles bloqueados o firewall?)"
        else -> "Error de red (${e.javaClass.simpleName}: ${e.message ?: "sin detalle"})"
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

    /** Petición HTTP a la API. Devuelve (código, cuerpo); lanza IaRed solo si no hubo respuesta de red. */
    private fun pedir(metodo: String, ruta: String, cuerpo: String?): Pair<Int, String> {
        val conn = URL(RAIZ + ruta).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = metodo
            conn.connectTimeout = 20000
            conn.readTimeout = 90000                       // los modelos con imagen pueden tardar
            conn.setRequestProperty("x-goog-api-key", clave())
            if (cuerpo != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            }
            val codigo = conn.responseCode
            val texto = (if (codigo in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return codigo to texto
        } catch (e: IOException) {
            throw IaRed(describir(e))
        } finally {
            conn.disconnect()
        }
    }

    private fun llamar(modelo: String, cuerpo: JSONObject): AnalisisIa {
        val (codigo, texto) = pedir("POST", "models/$modelo:generateContent", cuerpo.toString())
        if (codigo !in 200..299) {
            val detalle = mensajeDeGoogle(texto)
            throw IaHttp(
                codigo,
                when (codigo) {
                    400 -> "La IA rechazó la petición (400): $detalle"
                    401, 403 -> "La clave de IA no es válida o no tiene permiso (HTTP $codigo): $detalle"
                    402 -> "Tu clave de Gemini está en un proyecto con facturación sin saldo. Usa una clave de un proyecto SIN facturación (aistudio.google.com/apikey)"
                    404 -> "No existe el modelo $modelo: $detalle"
                    429 -> "La IA está saturada o se acabó el cupo gratuito por ahora. Intenta en un minuto"
                    else -> "La IA no respondió bien (HTTP $codigo): $detalle"
                }
            )
        }
        return parsear(texto)
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
