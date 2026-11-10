package com.recipestudio.kitchensync.data.session

data class Sustitucion(
    val original: String,
    val sustituto: String,
    val factorCantidad: Double,
    val impactoSaborPct: Int,
    val impactoTexturaPct: Int,
    val nota: String
)

object InsumoSubstitutionManager {
    private val equivalencias = mutableMapOf(
        "Mantequilla" to Sustitucion(
            original = "Mantequilla",
            sustituto = "Aceite de coco",
            factorCantidad = 0.9,
            impactoSaborPct = 15,
            impactoTexturaPct = 20,
            nota = "Sabor más neutro y textura ligeramente más ligera."
        ),
        "Leche Entera" to Sustitucion(
            original = "Leche Entera",
            sustituto = "Leche de almendras",
            factorCantidad = 1.0,
            impactoSaborPct = 18,
            impactoTexturaPct = 25,
            nota = "Más ligera y menos cremosa, ideal para salsas suaves."
        ),
        "Azúcar Blanca" to Sustitucion(
            original = "Azúcar Blanca",
            sustituto = "Azúcar morena",
            factorCantidad = 0.95,
            impactoSaborPct = 22,
            impactoTexturaPct = 12,
            nota = "Da un matiz más intenso y un toque caramelizado."
        ),
        "Azúcar" to Sustitucion(
            original = "Azúcar",
            sustituto = "Azúcar morena",
            factorCantidad = 0.95,
            impactoSaborPct = 22,
            impactoTexturaPct = 12,
            nota = "Equivalentemente dulce, con un perfil más tostado."
        ),
        "Ajo" to Sustitucion(
            original = "Ajo",
            sustituto = "Ajo en polvo",
            factorCantidad = 0.7,
            impactoSaborPct = 18,
            impactoTexturaPct = 10,
            nota = "Más intenso en sabor y menos textura fresca."
        ),
        "Harina de trigo" to Sustitucion(
            original = "Harina de trigo",
            sustituto = "Harina integral",
            factorCantidad = 0.9,
            impactoSaborPct = 20,
            impactoTexturaPct = 30,
            nota = "Masa más densa; puede necesitar un poco más de líquido."
        ),
        "Crema ácida" to Sustitucion(
            original = "Crema ácida",
            sustituto = "Yogur natural",
            factorCantidad = 1.0,
            impactoSaborPct = 15,
            impactoTexturaPct = 10,
            nota = "Más ligero y ácido; agrégalo fuera del fuego para que no se corte."
        ),
        "Queso manchego rallado" to Sustitucion(
            original = "Queso manchego rallado",
            sustituto = "Queso Oaxaca",
            factorCantidad = 1.0,
            impactoSaborPct = 20,
            impactoTexturaPct = 15,
            nota = "Más suave y elástico al gratinar."
        )
    )
    fun obtenerSustituto(nombreIngrediente: String): Sustitucion? {
        return equivalencias[nombreIngrediente]
    }

    /** Ingredientes para los que existe una regla de sustitución, sin duplicados de nombre. */
    fun listarOriginales(): List<String> = equivalencias.keys.sorted()

    fun obtenerNombreSustituto(nombreIngrediente: String): String {
        return obtenerSustituto(nombreIngrediente)?.sustituto ?: nombreIngrediente
    }

    fun registrarEquivalencia(original: String, sustituto: Sustitucion) {
        equivalencias[original] = sustituto
    }
}
