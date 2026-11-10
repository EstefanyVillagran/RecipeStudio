package com.recipestudio.kitchensync.domain

data class Ingrediente(
    val id: String,
    val nombre: String,
    val cantidadBase: Double,
    val unidad: String,
    var esCompletado: Boolean = false,
    val esSustituible: Boolean = true
) {
    fun marcarComoUsado() {
        this.esCompletado = !this.esCompletado
    }

    fun obtenerCantidadAjustada(porciones: Int): Double {
        return cantidadBase * porciones
    }
}
