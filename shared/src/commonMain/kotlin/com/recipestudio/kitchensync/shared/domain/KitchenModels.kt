package com.recipestudio.kitchensync.shared.domain

import kotlin.math.roundToInt

data class IngredientState(
    val id: String,
    val name: String,
    val quantity: String,
    val completed: Boolean = false
)

data class RecipeState(
    val id: String,
    val title: String,
    val category: String,
    val totalSteps: Int,
    val currentStep: Int = 1,
    val timeSeconds: Int = 180,
    val ingredients: List<IngredientState> = emptyList()
)

data class SessionQrPayload(
    val sessionId: String,
    val token: String,
    val baseUrl: String = "https://recipestudio.onrender.com"
) {
    fun toConnectionUrl(): String {
        val normalizedBase = baseUrl.trimEnd('/')
        return "$normalizedBase/conectar?sessionId=$sessionId&token=$token"
    }
}

data class SubstitutionModel(
    val original: String,
    val sustituto: String,
    val factorCantidad: Double,
    val impactoSaborPct: Int,
    val impactoTexturaPct: Int,
    val nota: String
) {
    fun cantidadAjustada(cantidadBase: Double): Double = (cantidadBase * factorCantidad * 10.0).roundToInt() / 10.0
}

data class KitchenEvent(
    val type: String,
    val sessionId: String,
    val ingredient: String? = null,
    val completed: Boolean? = null,
    val timerSeconds: Int? = null,
    val recipeName: String? = null,
    val token: String? = null
)
