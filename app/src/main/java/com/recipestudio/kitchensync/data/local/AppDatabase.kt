package com.recipestudio.kitchensync.data.local

import com.recipestudio.kitchensync.domain.Ingrediente
import com.recipestudio.kitchensync.domain.Receta

class AppDatabase {
    private val recetasLocales = mutableListOf<Receta>()
    private val ingredientesLocales = mutableListOf<Ingrediente>()

    fun guardarReceta(receta: Receta) {
        recetasLocales.removeAll { it.id == receta.id }
        recetasLocales.add(receta)
    }

    fun obtenerRecetas(): List<Receta> = recetasLocales.toList()

    fun obtenerRecetaPorId(id: String): Receta? = recetasLocales.firstOrNull { it.id == id }

    fun guardarIngrediente(ingrediente: Ingrediente) {
        ingredientesLocales.removeAll { it.id == ingrediente.id }
        ingredientesLocales.add(ingrediente)
    }

    fun obtenerIngredientes(): List<Ingrediente> = ingredientesLocales.toList()
}
