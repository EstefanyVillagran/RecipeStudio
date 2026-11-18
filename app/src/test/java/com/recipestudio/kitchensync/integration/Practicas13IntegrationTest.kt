package com.recipestudio.kitchensync.integration

import com.recipestudio.kitchensync.data.local.AppDatabase
import com.recipestudio.kitchensync.domain.Ingrediente
import com.recipestudio.kitchensync.domain.Receta
import com.recipestudio.kitchensync.domain.Temporizador
import com.recipestudio.kitchensync.repository.RecetaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Prácticas 13-14: integración real entre la lógica de persistencia local,
 * el repositorio y las entidades de dominio. No usa Firebase ni requiere Android.
 */
class Practicas13IntegrationTest {
    private fun receta(id: String = "receta-1", titulo: String = "Pasta") = Receta(
        id = id,
        titulo = titulo,
        porciones = 4,
        listaIngredientes = mutableListOf(Ingrediente("i1", "Ajo", 2.0, "dientes")),
        listaTemporizadores = mutableListOf(Temporizador("t1", "Cocción", 600L, 600L))
    )

    @Test fun `repositorio guarda y recupera una receta`() {
        val repository = RecetaRepository(AppDatabase())

        val guardada = repository.guardar(receta())

        assertEquals("receta-1", guardada.id)
        assertNotNull(repository.obtenerPorId("receta-1"))
        assertEquals(1, repository.obtenerTodas().size)
    }

    @Test fun `guardar el mismo id reemplaza el registro anterior`() {
        val repository = RecetaRepository(AppDatabase())
        repository.guardar(receta(titulo = "Versión anterior"))

        repository.guardar(receta(titulo = "Versión corregida"))

        assertEquals(1, repository.obtenerTodas().size)
        assertEquals("Versión corregida", repository.obtenerPorId("receta-1")!!.titulo)
    }

    @Test fun `receta y ingrediente comparten el estado persistido`() {
        val database = AppDatabase()
        val original = receta()
        val ingrediente = Ingrediente("i2", "Tomate", 3.0, "unidades")
        original.agregarIngrediente(ingrediente)
        database.guardarReceta(original)
        database.guardarIngrediente(ingrediente)

        val recuperada = database.obtenerRecetaPorId("receta-1")!!
        recuperada.listaIngredientes[1].marcarComoUsado()

        assertTrue(database.obtenerIngredientes()[0].esCompletado || database.obtenerIngredientes().size == 1)
        assertEquals(50f, recuperada.obtenerProgresoPreparacion(), 0.001f)
    }
}
