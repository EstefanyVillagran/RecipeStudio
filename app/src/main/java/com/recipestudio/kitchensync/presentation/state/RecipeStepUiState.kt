package com.recipestudio.kitchensync.presentation.state

data class RecipeStepUiState(
    val nombreReceta: String = "",
    val pasoActualNumero: Int = 1,
    val instruccionPaso: String = "",
    val tiempoRestanteSegundos: Int = 0,
    val temporizadorPausado: Boolean = false,
    val ingredientesCompletadosIds: Set<String> = emptySet(),
    val estaCargando: Boolean = false,
    val mensajeError: String? = null
)
