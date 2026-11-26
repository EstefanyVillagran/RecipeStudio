package com.recipestudio.kitchensync.domain

import com.recipestudio.kitchensync.data.session.InsumoSubstitutionManager
import kotlin.math.max
import kotlin.math.roundToInt

enum class NivelImpacto(val etiqueta: String) {
    BAJO("Bajo"), MODERADO("Moderado"), ALTO("Alto")
}

data class AnalisisSustitucion(
    val original: String,
    val sustituto: String,
    val cantidadOriginal: Double,
    val cantidadAjustada: Double,
    val impactoSaborPct: Int,
    val impactoTexturaPct: Int,
    val nivel: NivelImpacto,
    val nota: String
)

/** Calcula cuánta cantidad usar y cuánto cambia el sabor/textura al reemplazar un ingrediente. */
object SubstitutionCalculator {

    fun nivelDe(saborPct: Int, texturaPct: Int): NivelImpacto = when (max(saborPct, texturaPct)) {
        in 0..14 -> NivelImpacto.BAJO
        in 15..25 -> NivelImpacto.MODERADO
        else -> NivelImpacto.ALTO
    }

    /** @return null si no hay una regla para ese ingrediente. @throws IllegalArgumentException si la cantidad no es positiva. */
    fun analizar(original: String, cantidad: Double): AnalisisSustitucion? {
        require(cantidad > 0.0) { "La cantidad debe ser mayor que cero" }
        val clave = InsumoSubstitutionManager.listarOriginales().firstOrNull { it.equals(original.trim(), ignoreCase = true) }
            ?: return null
        val regla = InsumoSubstitutionManager.obtenerSustituto(clave) ?: return null
        return AnalisisSustitucion(
            original = regla.original,
            sustituto = regla.sustituto,
            cantidadOriginal = cantidad,
            cantidadAjustada = (cantidad * regla.factorCantidad * 10.0).roundToInt() / 10.0,
            impactoSaborPct = regla.impactoSaborPct,
            impactoTexturaPct = regla.impactoTexturaPct,
            nivel = nivelDe(regla.impactoSaborPct, regla.impactoTexturaPct),
            nota = regla.nota
        )
    }
}
