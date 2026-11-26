package com.recipestudio.kitchensync.domain

import kotlin.math.max
import kotlin.math.roundToInt

enum class FamiliaIngrediente { ENDULZANTE, GRASA, LACTEO, QUESO, ACIDO, SAL, PICANTE, ALLIUM, HIERBA, TOMATE, PROTEINA, CALDO, HARINA, HUEVO }

/** Perfil aproximado de un ingrediente: sabor (dulzor, sal, acidez, picante, herbal, profundo) y textura (grasa, cremosidad, humedad), de 0 a 10. */
class PerfilIngrediente(val nombre: String, val familia: FamiliaIngrediente, val sabor: IntArray, val textura: IntArray)

class EstimacionLocal(
    val falta: String, val tengo: String, val sirve: Boolean,
    val saborPct: Int, val texturaPct: Int, val consejo: String
)

/**
 * Estimador SIN internet y SIN IA: compara los perfiles de sabor y textura de dos ingredientes del catálogo.
 * Es una heurística (no una medición): sirve como respaldo cuando la IA no está disponible y para explicar el cálculo.
 *   sabor%   = 5 x (mayor diferencia) + 10 x (diferencia promedio)   sobre 6 rasgos de sabor
 *   textura% = 4 x (mayor diferencia) +  6 x (diferencia promedio)   sobre 3 rasgos de textura
 * "Sirve" si son de la misma familia (azúcar con azúcar, queso con queso...) y el cambio no es extremo.
 */
object EstimadorSabor {
    private fun P(n: String, f: FamiliaIngrediente, s: IntArray, t: IntArray) = PerfilIngrediente(n, f, s, t)

    val CATALOGO: List<PerfilIngrediente> = listOf(
        P("azucar blanca", FamiliaIngrediente.ENDULZANTE, intArrayOf(10,0,0,0,0,0), intArrayOf(0,0,0)),
        P("azucar morena", FamiliaIngrediente.ENDULZANTE, intArrayOf(9,0,0,0,0,3), intArrayOf(0,0,2)),
        P("piloncillo", FamiliaIngrediente.ENDULZANTE, intArrayOf(9,0,0,0,0,5), intArrayOf(0,0,1)),
        P("miel", FamiliaIngrediente.ENDULZANTE, intArrayOf(9,0,1,0,1,2), intArrayOf(0,2,6)),
        P("agave", FamiliaIngrediente.ENDULZANTE, intArrayOf(9,0,0,0,0,1), intArrayOf(0,1,7)),
        P("mantequilla", FamiliaIngrediente.GRASA, intArrayOf(0,1,0,0,0,3), intArrayOf(9,6,2)),
        P("margarina", FamiliaIngrediente.GRASA, intArrayOf(0,1,0,0,0,2), intArrayOf(9,6,2)),
        P("aceite vegetal", FamiliaIngrediente.GRASA, intArrayOf(0,0,0,0,0,0), intArrayOf(10,0,0)),
        P("aceite de oliva", FamiliaIngrediente.GRASA, intArrayOf(0,0,0,0,1,2), intArrayOf(10,0,0)),
        P("aceite de coco", FamiliaIngrediente.GRASA, intArrayOf(1,0,0,0,0,2), intArrayOf(10,2,0)),
        P("manteca", FamiliaIngrediente.GRASA, intArrayOf(0,1,0,0,0,4), intArrayOf(10,4,0)),
        P("leche entera", FamiliaIngrediente.LACTEO, intArrayOf(1,0,0,0,0,0), intArrayOf(2,4,9)),
        P("leche deslactosada", FamiliaIngrediente.LACTEO, intArrayOf(1,0,0,0,0,0), intArrayOf(2,4,9)),
        P("leche de almendras", FamiliaIngrediente.LACTEO, intArrayOf(1,0,0,0,0,1), intArrayOf(1,2,9)),
        P("crema", FamiliaIngrediente.LACTEO, intArrayOf(1,0,1,0,0,1), intArrayOf(7,8,5)),
        P("yogur natural", FamiliaIngrediente.LACTEO, intArrayOf(1,0,4,0,0,1), intArrayOf(3,7,6)),
        P("queso manchego", FamiliaIngrediente.QUESO, intArrayOf(0,5,1,0,0,5), intArrayOf(7,3,2)),
        P("queso oaxaca", FamiliaIngrediente.QUESO, intArrayOf(0,4,1,0,0,4), intArrayOf(6,5,3)),
        P("queso fresco", FamiliaIngrediente.QUESO, intArrayOf(0,4,1,0,0,2), intArrayOf(4,4,5)),
        P("queso panela", FamiliaIngrediente.QUESO, intArrayOf(0,3,1,0,0,2), intArrayOf(3,3,6)),
        P("queso cotija", FamiliaIngrediente.QUESO, intArrayOf(0,8,1,0,0,5), intArrayOf(5,2,2)),
        P("queso parmesano", FamiliaIngrediente.QUESO, intArrayOf(0,7,1,0,0,8), intArrayOf(6,1,1)),
        P("limon", FamiliaIngrediente.ACIDO, intArrayOf(0,0,9,0,1,0), intArrayOf(0,0,9)),
        P("lima", FamiliaIngrediente.ACIDO, intArrayOf(0,0,8,0,2,0), intArrayOf(0,0,9)),
        P("vinagre blanco", FamiliaIngrediente.ACIDO, intArrayOf(0,0,10,0,0,0), intArrayOf(0,0,10)),
        P("vinagre de manzana", FamiliaIngrediente.ACIDO, intArrayOf(1,0,8,0,0,1), intArrayOf(0,0,10)),
        P("naranja agria", FamiliaIngrediente.ACIDO, intArrayOf(1,0,7,0,1,0), intArrayOf(0,0,9)),
        P("sal", FamiliaIngrediente.SAL, intArrayOf(0,10,0,0,0,0), intArrayOf(0,0,0)),
        P("chile serrano", FamiliaIngrediente.PICANTE, intArrayOf(0,0,1,8,2,0), intArrayOf(0,0,6)),
        P("chile jalapeno", FamiliaIngrediente.PICANTE, intArrayOf(0,0,1,6,2,0), intArrayOf(0,0,6)),
        P("chile de arbol", FamiliaIngrediente.PICANTE, intArrayOf(0,0,0,9,0,2), intArrayOf(0,0,1)),
        P("chile pasilla", FamiliaIngrediente.PICANTE, intArrayOf(0,0,1,4,1,5), intArrayOf(0,0,2)),
        P("pimienta", FamiliaIngrediente.PICANTE, intArrayOf(0,0,0,4,1,2), intArrayOf(0,0,0)),
        P("ajo", FamiliaIngrediente.ALLIUM, intArrayOf(0,0,0,3,2,3), intArrayOf(0,0,5)),
        P("ajo en polvo", FamiliaIngrediente.ALLIUM, intArrayOf(0,0,0,2,1,4), intArrayOf(0,0,0)),
        P("cebolla", FamiliaIngrediente.ALLIUM, intArrayOf(1,0,1,2,1,1), intArrayOf(0,0,8)),
        P("cebolla morada", FamiliaIngrediente.ALLIUM, intArrayOf(2,0,1,2,1,1), intArrayOf(0,0,8)),
        P("cebolla en polvo", FamiliaIngrediente.ALLIUM, intArrayOf(2,0,0,1,0,3), intArrayOf(0,0,0)),
        P("cilantro", FamiliaIngrediente.HIERBA, intArrayOf(0,0,1,0,9,0), intArrayOf(0,0,6)),
        P("perejil", FamiliaIngrediente.HIERBA, intArrayOf(0,0,0,0,8,0), intArrayOf(0,0,6)),
        P("albahaca", FamiliaIngrediente.HIERBA, intArrayOf(0,0,0,0,9,1), intArrayOf(0,0,6)),
        P("oregano", FamiliaIngrediente.HIERBA, intArrayOf(0,0,0,1,8,2), intArrayOf(0,0,1)),
        P("epazote", FamiliaIngrediente.HIERBA, intArrayOf(0,0,0,0,9,1), intArrayOf(0,0,6)),
        P("jitomate", FamiliaIngrediente.TOMATE, intArrayOf(2,0,3,0,1,3), intArrayOf(0,0,8)),
        P("tomatillo", FamiliaIngrediente.TOMATE, intArrayOf(1,0,6,0,1,1), intArrayOf(0,0,8)),
        P("pasta de tomate", FamiliaIngrediente.TOMATE, intArrayOf(3,1,4,0,0,6), intArrayOf(0,2,4)),
        P("pechuga de pollo", FamiliaIngrediente.PROTEINA, intArrayOf(0,1,0,0,0,3), intArrayOf(2,1,6)),
        P("carne de res", FamiliaIngrediente.PROTEINA, intArrayOf(0,1,0,0,0,6), intArrayOf(4,1,5)),
        P("carne de cerdo", FamiliaIngrediente.PROTEINA, intArrayOf(0,1,0,0,0,5), intArrayOf(6,1,5)),
        P("caldo de pollo", FamiliaIngrediente.CALDO, intArrayOf(0,4,0,0,1,6), intArrayOf(0,0,10)),
        P("caldo de res", FamiliaIngrediente.CALDO, intArrayOf(0,4,0,0,1,7), intArrayOf(0,0,10)),
        P("caldo de verduras", FamiliaIngrediente.CALDO, intArrayOf(1,4,0,0,2,4), intArrayOf(0,0,10)),
        P("harina de trigo", FamiliaIngrediente.HARINA, intArrayOf(0,0,0,0,0,1), intArrayOf(0,0,0)),
        P("harina de maiz", FamiliaIngrediente.HARINA, intArrayOf(1,0,0,0,0,2), intArrayOf(0,0,0)),
        P("maizena", FamiliaIngrediente.HARINA, intArrayOf(0,0,0,0,0,0), intArrayOf(0,0,0)),
        P("pan molido", FamiliaIngrediente.HARINA, intArrayOf(0,1,0,0,0,2), intArrayOf(0,0,0)),
        P("huevo", FamiliaIngrediente.HUEVO, intArrayOf(0,1,0,0,0,3), intArrayOf(5,5,6))
    )

    private val ALIAS = mapOf(
        "tomate" to "jitomate",
        "azucar" to "azucar blanca",
        "pollo" to "pechuga de pollo",
        "res" to "carne de res",
        "cerdo" to "carne de cerdo",
        "aceite" to "aceite vegetal",
        "leche" to "leche entera",
        "queso" to "queso fresco",
        "chile" to "chile serrano",
        "cebollin" to "cebolla",
        "vinagre" to "vinagre blanco",
        "harina" to "harina de trigo",
        "caldo" to "caldo de pollo",
        "naranja" to "naranja agria",
        "yogurt" to "yogur natural",
        "jitomates" to "jitomate",
        "yogurt natural" to "yogur natural"
    )

    private val RUIDO = setOf("al", "de", "del", "el", "extra", "fresca", "frescas", "fresco", "frescos", "g", "gusto", "kg", "la", "las", "los", "madura", "maduras", "maduro", "maduros", "ml", "molida", "molido", "para", "picada", "picado", "pza", "pzas", "rallada", "rallado", "sin", "taza", "tazas", "un", "una", "unas", "unos", "virgen")
    private val RASGOS_SABOR = listOf("dulzor", "sal", "acidez", "picante", "sabor herbal", "sabor tostado o profundo")
    private val RASGOS_TEXTURA = listOf("grasa", "cremosidad", "humedad")

    private fun sinAcentos(s: String): String = s.lowercase().map {
        when (it) { 'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; 'ü' -> 'u'; 'ñ' -> 'n'; else -> it }
    }.joinToString("")

    /** Palabras significativas: sin acentos, sin "fresco/rallado/de...", y en singular. */
    internal fun palabras(texto: String): List<String> =
        sinAcentos(texto).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && it !in RUIDO }
            .map { if (it.length > 3 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }

    /** Busca el ingrediente del catálogo que corresponde a lo que escribió la persona ("Queso manchego rallado" -> queso manchego). */
    fun buscar(texto: String): PerfilIngrediente? {
        val q = palabras(texto)
        if (q.isEmpty()) return null
        ALIAS.entries.firstOrNull { palabras(it.key) == q }?.let { a -> return CATALOGO.first { it.nombre == a.value } }
        val qs = q.toSet()
        var mejor: PerfilIngrediente? = null
        var mejorPuntos = 0
        for (e in CATALOGO) {
            val et = palabras(e.nombre).toSet()
            val puntos = when {
                et.isNotEmpty() && qs.containsAll(et) -> et.size * 10   // el nombre completo aparece: gana el más específico
                et.containsAll(qs) -> qs.size                            // solo escribió una parte (ej. "azúcar")
                else -> 0
            }
            if (puntos > mejorPuntos) { mejor = e; mejorPuntos = puntos }
        }
        return mejor
    }

    private fun porcentaje(a: IntArray, b: IntArray, pesoMax: Int, pesoMedia: Int): Int {
        val d = a.indices.map { kotlin.math.abs(a[it] - b[it]) }
        return minOf(100, (d.max() * pesoMax + d.sum().toDouble() / d.size * pesoMedia).roundToInt())
    }

    /** null si alguno de los dos ingredientes no está en el catálogo. */
    fun estimar(falta: String, tengo: String): EstimacionLocal? {
        val a = buscar(falta) ?: return null
        val b = buscar(tengo) ?: return null
        val sabor = porcentaje(a.sabor, b.sabor, 5, 10)
        val textura = porcentaje(a.textura, b.textura, 4, 6)
        val sirve = a.familia == b.familia && sabor <= 45 && textura <= 60
        return EstimacionLocal(a.nombre, b.nombre, sirve, sabor, textura, consejo(a, b, sirve, sabor, textura))
    }

    private fun consejo(a: PerfilIngrediente, b: PerfilIngrediente, sirve: Boolean, sabor: Int, textura: Int): String {
        if (a.familia != b.familia) return "Son de tipos distintos de ingrediente: el platillo cambiará mucho. Mejor busca otra opción."
        if (!sirve) return "El cambio es muy grande para esta receta."
        if (sabor <= 10 && textura <= 15) return "Casi no se nota la diferencia: úsalo en la misma cantidad."
        val ds = a.sabor.indices.map { b.sabor[it] - a.sabor[it] }
        val dt = a.textura.indices.map { b.textura[it] - a.textura[it] }
        val iS = ds.indices.maxByOrNull { kotlin.math.abs(ds[it]) }!!
        val iT = dt.indices.maxByOrNull { kotlin.math.abs(dt[it]) }!!
        val usarSabor = kotlin.math.abs(ds[iS]) >= kotlin.math.abs(dt[iT])
        val mas = if (usarSabor) ds[iS] > 0 else dt[iT] > 0
        val rasgo = if (usarSabor) RASGOS_SABOR[iS] else RASGOS_TEXTURA[iT]
        return "Notarás " + (if (mas) "más " else "menos ") + rasgo + ". Empieza con un poco menos y ajusta al probar."
    }
}
