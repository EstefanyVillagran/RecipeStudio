package com.recipestudio.kitchensync.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubstitutionCalculatorTest {
    @Test fun `azucar blanca por morena ajusta cantidad y sabor`() {
        val a = SubstitutionCalculator.analizar("Azúcar Blanca", 100.0)
        assertNotNull(a)
        assertEquals("Azúcar morena", a!!.sustituto)
        assertEquals(95.0, a.cantidadAjustada, 0.001)
        assertEquals(22, a.impactoSaborPct)
        assertEquals(NivelImpacto.MODERADO, a.nivel)
    }

    @Test fun `busqueda ignora mayusculas`() = assertNotNull(SubstitutionCalculator.analizar("azúcar blanca", 10.0))
    @Test fun `ingrediente sin regla devuelve null`() = assertNull(SubstitutionCalculator.analizar("Trufa", 10.0))
    @Test(expected = IllegalArgumentException::class) fun `cantidad cero lanza error`() { SubstitutionCalculator.analizar("Ajo", 0.0) }
    @Test fun `ingrediente vacio devuelve null`() = assertNull(SubstitutionCalculator.analizar("   ", 10.0))

    @Test(expected = IllegalArgumentException::class)
    fun `cantidad infinita lanza error`() { SubstitutionCalculator.analizar("Ajo", Double.POSITIVE_INFINITY) }

    @Test(expected = IllegalArgumentException::class)
    fun `cantidad NaN lanza error`() { SubstitutionCalculator.analizar("Ajo", Double.NaN) }

    @Test fun `resultado valida limites de impacto`() {
        val a = SubstitutionCalculator.analizar("Ajo", 50.0)
        assertNotNull(a)
        assertTrue(a!!.cantidadAjustada > 0.0)
        assertEquals(35.0, a.cantidadAjustada, 0.001)
        assertEquals(NivelImpacto.MODERADO, a.nivel)
    }

    @Test fun `niveles de impacto`() {
        assertEquals(NivelImpacto.BAJO, SubstitutionCalculator.nivelDe(10, 5))
        assertEquals(NivelImpacto.MODERADO, SubstitutionCalculator.nivelDe(15, 5))
        assertEquals(NivelImpacto.ALTO, SubstitutionCalculator.nivelDe(10, 30))
    }
}
