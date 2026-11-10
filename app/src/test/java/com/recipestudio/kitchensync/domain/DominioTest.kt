package com.recipestudio.kitchensync.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DominioTest {
    private fun receta() = Receta(
        "r1", "Pasta", 4,
        mutableListOf(
            Ingrediente("1", "Spaghetti", 400.0, "g", esCompletado = true),
            Ingrediente("2", "Ajo", 2.0, "dientes")
        ),
        mutableListOf(Temporizador("t1", "Pasta", 900L, 900L))
    )

    @Test fun `progreso de la receta`() = assertEquals(50f, receta().obtenerProgresoPreparacion(), 0.01f)
    @Test fun `ingredientes pendientes`() = assertEquals(1, receta().obtenerIngredientesPendientes())

    @Test fun `marcar ingrediente alterna el estado`() {
        val i = Ingrediente("1", "Ajo", 1.0, "pz")
        i.marcarComoUsado(); assertTrue(i.esCompletado)
        i.marcarComoUsado(); assertFalse(i.esCompletado)
    }

    @Test fun `temporizador iniciar pausar reiniciar`() {
        val t = Temporizador("t", "x", 100L, 40L)
        t.iniciar(); assertEquals(EstadoTemporizador.CORRIENDO, t.estado)
        t.pausar(); assertEquals(EstadoTemporizador.PAUSADO, t.estado)
        assertEquals(60f, t.obtenerPorcentajeProgreso(), 0.01f)
        t.reiniciar(); assertEquals(100L, t.tiempoRestanteSegundos)
        assertEquals(EstadoTemporizador.DETENIDO, t.estado)
    }

    @Test fun `sesion se vincula al confirmar`() {
        val s = SesionCocina("S1", "T1")
        assertFalse(s.dispositivoMovilConectado)
        s.confirmarConexion(); assertTrue(s.dispositivoMovilConectado)
    }
}
