package com.recipestudio.kitchensync.whitebox

import com.recipestudio.kitchensync.network.GeminiSustitucion
import com.recipestudio.kitchensync.network.IaException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lectura de la respuesta de la IA (GeminiSustitucion.parsear): ramas de éxito, JSON con cercas, valores fuera de rango y errores. */
class GeminiParsearWhiteBoxTest {
    private fun envolver(texto: String) =
        """{"candidates":[{"content":{"parts":[{"text":${org.json.JSONObject.quote(texto)}}]}}]}"""

    private val ok = """{"producto":"Azúcar morena","sirve":true,"impactoSaborPct":18,"impactoTexturaPct":10,
        "cantidadSugerida":"1 taza","nota":"Más sabor a caramelo","confianza":"alta","consejo":"Mezcla bien","alternativas":["Miel","Piloncillo","Extra"]}"""

    @Test fun respuesta_completa() {
        val a = GeminiSustitucion.parsear(envolver(ok))
        assertEquals("Azúcar morena", a.producto); assertTrue(a.sirve)
        assertEquals(18, a.impactoSaborPct); assertEquals("alta", a.confianza)
        assertEquals(listOf("Miel", "Piloncillo"), a.alternativas)          // máximo 2 alternativas
    }

    @Test fun json_dentro_de_cercas_markdown() =
        assertEquals("Azúcar morena", GeminiSustitucion.parsear(envolver("```json\n$ok\n```")).producto)

    @Test fun porcentajes_fuera_de_rango_se_recortan() {
        val a = GeminiSustitucion.parsear(envolver("""{"producto":"X","sirve":true,"impactoSaborPct":250,"impactoTexturaPct":-5,"cantidadSugerida":"","nota":""}"""))
        assertEquals(100, a.impactoSaborPct); assertEquals(0, a.impactoTexturaPct)
    }

    @Test fun confianza_desconocida_vuelve_a_media_y_campos_opcionales_vacios() {
        val a = GeminiSustitucion.parsear(envolver("""{"producto":"X","sirve":false,"impactoSaborPct":1,"impactoTexturaPct":1,"cantidadSugerida":"","nota":"n","confianza":"rarisima"}"""))
        assertEquals("media", a.confianza); assertFalse(a.sirve); assertTrue(a.alternativas.isEmpty())
    }

    @Test(expected = IaException::class) fun sin_candidatos_lanza_error_claro() { GeminiSustitucion.parsear("""{"candidates":[]}""") }
    @Test(expected = IaException::class) fun texto_que_no_es_json_lanza_error() { GeminiSustitucion.parsear(envolver("hola, no soy json")) }
    @Test(expected = IaException::class) fun respuesta_ilegible_lanza_error() { GeminiSustitucion.parsear("<html>502</html>") }
}
