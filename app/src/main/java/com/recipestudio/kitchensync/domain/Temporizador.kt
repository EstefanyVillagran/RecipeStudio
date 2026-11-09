package com.recipestudio.kitchensync.domain

enum class EstadoTemporizador {
    DETENIDO,
    CORRIENDO,
    PAUSADO
}

data class Temporizador(
    val id: String,
    val etiqueta: String,
    val duracionTotalSegundos: Long,
    var tiempoRestanteSegundos: Long,
    var estado: EstadoTemporizador = EstadoTemporizador.DETENIDO
) {
    fun iniciar() {
        estado = EstadoTemporizador.CORRIENDO
    }

    fun pausar() {
        estado = EstadoTemporizador.PAUSADO
    }

    fun reiniciar() {
        tiempoRestanteSegundos = duracionTotalSegundos
        estado = EstadoTemporizador.DETENIDO
    }

    fun obtenerPorcentajeProgreso(): Float {
        if (duracionTotalSegundos == 0L) return 0f
        return ((duracionTotalSegundos - tiempoRestanteSegundos).toFloat() / duracionTotalSegundos.toFloat()) * 100
    }
}
