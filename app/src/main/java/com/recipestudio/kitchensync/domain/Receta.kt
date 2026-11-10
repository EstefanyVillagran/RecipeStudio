package com.recipestudio.kitchensync.domain

data class Receta(
    val id: String,
    val titulo: String,
    val porciones: Int,
    val listaIngredientes: MutableList<Ingrediente>,
    val listaTemporizadores: MutableList<Temporizador>,
    val esSecreta: Boolean = false
) {
    fun agregarIngrediente(ingrediente: Ingrediente) {
        listaIngredientes.add(ingrediente)
    }

    fun obtenerProgresoPreparacion(): Float {
        if (listaIngredientes.isEmpty()) return 0f
        val completados = listaIngredientes.count { it.esCompletado }
        return (completados.toFloat() / listaIngredientes.size.toFloat()) * 100
    }

    fun obtenerIngredientesPendientes(): Int {
        return listaIngredientes.count { !it.esCompletado }
    }
}
