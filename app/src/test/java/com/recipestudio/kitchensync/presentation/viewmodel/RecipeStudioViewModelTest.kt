package com.recipestudio.kitchensync.presentation.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeStudioViewModelTest {

    @Test
    fun `base recipe loads expected values`() {
        val viewModel = RecipeStudioViewModel()

        assertEquals("Pasta al pomodoro", viewModel.uiState.value.nombreReceta)
        assertEquals(3, viewModel.uiState.value.pasoActualNumero)
        assertEquals(180, viewModel.uiState.value.tiempoRestanteSegundos)
    }

    @Test
    fun `toggle ingredient updates completed ids`() {
        val viewModel = RecipeStudioViewModel()

        assertFalse(viewModel.uiState.value.ingredientesCompletadosIds.contains("3"))

        viewModel.marcarIngredienteComoUtilizado("3")
        assertTrue(viewModel.uiState.value.ingredientesCompletadosIds.contains("3"))

        viewModel.marcarIngredienteComoUtilizado("3")
        assertFalse(viewModel.uiState.value.ingredientesCompletadosIds.contains("3"))
    }
}
