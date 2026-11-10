package com.recipestudio.kitchensync.presentation.viewmodel

import com.recipestudio.kitchensync.data.session.InsumoSubstitutionManager
import com.recipestudio.kitchensync.domain.Receta
import com.recipestudio.kitchensync.network.S3StorageService
import com.recipestudio.kitchensync.presentation.state.RecipeStepUiState
import com.recipestudio.kitchensync.repository.RecetaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecipeStudioViewModel(
    private val recetaRepository: RecetaRepository = RecetaRepository(),
    private val s3StorageService: S3StorageService = S3StorageService()
) {
    private val _uiState = MutableStateFlow(RecipeStepUiState())
    val uiState: StateFlow<RecipeStepUiState> = _uiState.asStateFlow()

    init {
        cargarRecetaBase()
    }

    fun cargarRecetaBase() {
        val receta = recetaRepository.obtenerRecetaEjemplo()
        val idsCompletados = receta.listaIngredientes
            .filter { it.esCompletado }
            .map { it.id }
            .toSet()

        _uiState.value = RecipeStepUiState(
            nombreReceta = receta.titulo,
            pasoActualNumero = 3,
            instruccionPaso = "Añade los tomates triturados a la sartén y cocina a fuego medio, removiendo ocasionalmente, hasta que la salsa espese y adquiera un color rojo intenso.",
            tiempoRestanteSegundos = 180,
            temporizadorPausado = false,
            ingredientesCompletadosIds = idsCompletados,
            estaCargando = false,
            mensajeError = null
        )
    }

    fun guardarNuevaReceta(receta: Receta, onResultado: (Boolean) -> Unit) {
        try {
            recetaRepository.guardar(receta)
            onResultado(true)
        } catch (e: Exception) {
            onResultado(false)
        }
    }

    fun subirFotoRecetaACloud(bytesFoto: ByteArray, nombreArchivo: String) {
        s3StorageService.subirImagen(nombreArchivo, bytesFoto)
    }

    fun marcarIngredienteComoUtilizado(ingredienteId: String) {
        val actuales = _uiState.value.ingredientesCompletadosIds.toMutableSet()
        if (actuales.contains(ingredienteId)) {
            actuales.remove(ingredienteId)
        } else {
            actuales.add(ingredienteId)
        }
        _uiState.value = _uiState.value.copy(ingredientesCompletadosIds = actuales)
    }

    fun alternarPausaTemporizador() {
        _uiState.value = _uiState.value.copy(
            temporizadorPausado = !_uiState.value.temporizadorPausado
        )
    }

    fun actualizarTiempoRestante(segundos: Int) {
        _uiState.value = _uiState.value.copy(
            tiempoRestanteSegundos = segundos.coerceAtLeast(0)
        )
    }

    fun avanzarPaso() {
        _uiState.value = _uiState.value.copy(
            pasoActualNumero = _uiState.value.pasoActualNumero + 1
        )
    }

    fun solicitarSustituto(nombreIngrediente: String): String {
        return InsumoSubstitutionManager.obtenerSustituto(nombreIngrediente)?.sustituto
            ?: nombreIngrediente
    }
}
