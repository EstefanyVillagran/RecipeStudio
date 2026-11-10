package com.recipestudio.kitchensync.repository

import com.recipestudio.kitchensync.data.local.AppDatabase
import com.recipestudio.kitchensync.domain.Ingrediente
import com.recipestudio.kitchensync.domain.Receta
import com.recipestudio.kitchensync.domain.Temporizador

class RecetaRepository(
    private val database: AppDatabase = AppDatabase()
) {
    fun guardar(receta: Receta): Receta {
        database.guardarReceta(receta)
        return receta
    }

    fun obtenerPorId(id: String): Receta? = database.obtenerRecetaPorId(id)

    fun obtenerTodas(): List<Receta> = database.obtenerRecetas()

    fun obtenerRecetaEjemplo(): Receta {
        val ingredientes = mutableListOf(
            Ingrediente("1", "Spaghetti", 400.0, "g", esCompletado = true),
            Ingrediente("2", "Tomates maduros", 6.0, "unidades", esCompletado = true),
            Ingrediente("3", "Ajo fresco", 2.0, "dientes"),
            Ingrediente("4", "Albahaca fresca", 1.0, "manojo"),
            Ingrediente("5", "Aceite de oliva extra virgen", 45.0, "ml")
        )

        val temporizadores = mutableListOf(
            Temporizador("t1", "Cocción de pasta", 900L, 900L),
            Temporizador("t2", "Salsa", 900L, 900L),
            Temporizador("t3", "Emplatar", 180L, 180L)
        )

        val receta = Receta(
            id = "r101",
            titulo = "Pasta al pomodoro",
            porciones = 4,
            listaIngredientes = ingredientes,
            listaTemporizadores = temporizadores
        )

        database.guardarReceta(receta)
        return receta
    }
}
