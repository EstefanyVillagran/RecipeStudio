package com.recipestudio.kitchensync.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Estimador local (sin IA): búsqueda de ingredientes por nombre y porcentajes de cambio de sabor y textura. */
class EstimadorSaborTest {
    @Test fun busca_ignorando_acentos_mayusculas_plurales_y_palabras_de_relleno() {
        assertEquals("aceite de oliva", EstimadorSabor.buscar("Aceite de oliva extra virgen")?.nombre)
        assertEquals("queso manchego", EstimadorSabor.buscar("QUESO MANCHEGO rallado")?.nombre)
        assertEquals("jitomate", EstimadorSabor.buscar("Tomates maduros")?.nombre)     // alias + plural
        assertEquals("azucar blanca", EstimadorSabor.buscar("Azúcar")?.nombre)           // solo escribió una parte
        assertEquals("limon", EstimadorSabor.buscar("limón verde")?.nombre)
    }

    @Test fun ingrediente_desconocido_o_vacio_devuelve_null() {
        assertNull(EstimadorSabor.buscar("Spaghetti"))
        assertNull(EstimadorSabor.buscar("   "))
        assertNull(EstimadorSabor.estimar("Spaghetti", "Sal"))
        assertNull(EstimadorSabor.estimar("Sal", "Trufa negra"))
    }

    @Test fun azucar_blanca_por_morena_es_un_cambio_moderado() {
        val e = assertNotNull(EstimadorSabor.estimar("Azúcar blanca", "Azúcar morena"))
        assertEquals(22, e.saborPct); assertEquals(12, e.texturaPct); assertTrue(e.sirve)
    }

    @Test fun quesos_se_parecen() {
        val e = assertNotNull(EstimadorSabor.estimar("Queso manchego rallado", "Queso Oaxaca"))
        assertEquals(8, e.saborPct); assertEquals(16, e.texturaPct); assertTrue(e.sirve)
    }

    @Test fun mantequilla_por_aceite_cambia_la_textura() {
        val e = assertNotNull(EstimadorSabor.estimar("Mantequilla", "Aceite vegetal"))
        assertEquals(22, e.saborPct); assertEquals(42, e.texturaPct); assertTrue(e.sirve)
        assertTrue(e.consejo.contains("cremosidad"))
    }

    @Test fun ingredientes_de_familias_distintas_no_sirven() {
        val e = assertNotNull(EstimadorSabor.estimar("Azúcar blanca", "Sal"))
        assertEquals(83, e.saborPct); assertFalse(e.sirve)
        assertTrue(e.consejo.contains("tipos distintos"))
    }

    @Test fun el_mismo_ingrediente_no_cambia_nada() {
        val e = assertNotNull(EstimadorSabor.estimar("Cilantro", "cilantro fresco"))
        assertEquals(0, e.saborPct); assertEquals(0, e.texturaPct); assertTrue(e.sirve)
        assertTrue(e.consejo.contains("Casi no se nota"))
    }

    @Test fun los_porcentajes_siempre_estan_entre_0_y_100() {
        for (a in EstimadorSabor.CATALOGO) for (b in EstimadorSabor.CATALOGO) {
            val e = assertNotNull(EstimadorSabor.estimar(a.nombre, b.nombre))
            assertTrue(e.saborPct in 0..100 && e.texturaPct in 0..100, "${a.nombre} -> ${b.nombre}")
        }
    }

    @Test fun el_catalogo_no_repite_nombres_y_todo_perfil_va_de_0_a_10() {
        assertEquals(EstimadorSabor.CATALOGO.size, EstimadorSabor.CATALOGO.map { it.nombre }.toSet().size)
        EstimadorSabor.CATALOGO.forEach { p ->
            assertEquals(6, p.sabor.size); assertEquals(3, p.textura.size)
            assertTrue((p.sabor + p.textura).all { it in 0..10 }, p.nombre)
        }
    }
}
