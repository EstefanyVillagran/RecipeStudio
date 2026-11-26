package com.recipestudio.kitchensync.domain

import com.recipestudio.kitchensync.domain.NivelImpacto
import com.recipestudio.kitchensync.domain.SubstitutionCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Práctica 11 - Fase 2. MÉTODO DE CÁLCULO: SubstitutionCalculator.analizar(original, cantidad) y nivelDe(sabor, textura)
 *
 * analizar():  require(cantidad > 0) -> [P1 excepción]
 *              clave = buscar (ignora mayúsculas) ?: return null          -> [P2 sin regla]
 *              regla = obtenerSustituto(clave) ?: return null              -> [P3 CÓDIGO INALCANZABLE: la clave viene de listarOriginales()]
 *              cálculo del resultado                                       -> [P4 éxito]
 * nivelDe():   when(max(sabor, textura)) { 0..14 BAJO, 15..25 MODERADO, else ALTO }
 */
class SustitucionWhiteBoxTest {

    // ---- analizar(): caminos ----
    @Test fun p1_cantidad_cero_lanza_excepcion() { assertFailsWith<IllegalArgumentException> { SubstitutionCalculator.analizar("Ajo", 0.0) } }
    @Test fun p1_cantidad_negativa_lanza_excepcion() { assertFailsWith<IllegalArgumentException> { SubstitutionCalculator.analizar("Ajo", -5.0) } }
    @Test fun p1_mensaje_de_la_excepcion() {
        val e = runCatching { SubstitutionCalculator.analizar("Ajo", 0.0) }.exceptionOrNull()
        assertEquals("La cantidad debe ser mayor que cero y finita", e?.message)
    }
    @Test fun p2_ingrediente_sin_regla_devuelve_null() = assertNull(SubstitutionCalculator.analizar("Trufa negra", 10.0))
    @Test fun p2_texto_vacio_devuelve_null() = assertNull(SubstitutionCalculator.analizar("", 10.0))
    @Test fun p4_exito_con_regla() {
        val a = SubstitutionCalculator.analizar("Azúcar Blanca", 100.0)
        assertNotNull(a)
        assertEquals("Azúcar morena", a!!.sustituto)
        assertEquals(95.0, a.cantidadAjustada, 0.0001)
    }
    @Test fun p4_busqueda_ignora_mayusculas_y_espacios() {
        assertNotNull(SubstitutionCalculator.analizar("  AZÚCAR blanca  ", 10.0))
    }

    // ---- valores límite de la cantidad ----
    @Test fun limite_cantidad_muy_pequena_se_redondea_a_un_decimal() {
        val a = SubstitutionCalculator.analizar("Azúcar Blanca", 0.01)
        assertEquals(0.0, a!!.cantidadAjustada, 0.0001)   // 0.01 * 0.95 -> 0.0095 -> redondeo a 0.0
    }
    @Test fun limite_cantidad_grande() {
        assertNotNull(SubstitutionCalculator.analizar("Azúcar Blanca", 1_000_000.0))
    }

    // ---- nivelDe(): decisiones y valores límite (14/15 y 25/26) ----
    @Test fun nivel_0_es_bajo() = assertEquals(NivelImpacto.BAJO, SubstitutionCalculator.nivelDe(0, 0))
    @Test fun nivel_14_es_bajo() = assertEquals(NivelImpacto.BAJO, SubstitutionCalculator.nivelDe(14, 3))
    @Test fun nivel_15_es_moderado() = assertEquals(NivelImpacto.MODERADO, SubstitutionCalculator.nivelDe(15, 3))
    @Test fun nivel_25_es_moderado() = assertEquals(NivelImpacto.MODERADO, SubstitutionCalculator.nivelDe(25, 0))
    @Test fun nivel_26_es_alto() = assertEquals(NivelImpacto.ALTO, SubstitutionCalculator.nivelDe(26, 0))
    @Test fun nivel_usa_el_mayor_de_los_dos() = assertEquals(NivelImpacto.ALTO, SubstitutionCalculator.nivelDe(5, 40))

    // ---- HALLAZGO: porcentajes fuera de 0..100 caen en 'else' (ALTO) sin validarse ----
    @Test fun hallazgo_porcentaje_negativo_se_clasifica_como_alto() = assertEquals(NivelImpacto.ALTO, SubstitutionCalculator.nivelDe(-1, -1))
}
