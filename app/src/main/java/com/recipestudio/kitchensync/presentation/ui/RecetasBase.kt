package com.recipestudio.kitchensync.presentation.ui

import com.recipestudio.kitchensync.data.remote.RecetaPropia
import org.json.JSONArray
import org.json.JSONObject

/** Pasos de las dos recetas originales (que solo tenían una instrucción). */
private val PASOS_ORIGINALES = mapOf(
    "Pasta al pomodoro" to listOf(
        "Hierve la pasta en agua con sal hasta que esté al dente.",
        "Sofríe el ajo y agrega los tomates triturados.",
        "Cocina la salsa a fuego medio hasta que espese y se ponga roja.",
        "Pasa la pasta a la sartén con un poco del agua de cocción y mezcla.",
        "Termina con albahaca fresca y aceite de oliva."
    ),
    "Enchiladas Suizas" to listOf(
        "Cuece y deshebra el pollo.",
        "Calienta las tortillas y rellénalas con el pollo.",
        "Licúa los tomatillos con crema para hacer la salsa verde.",
        "Baña las enchiladas con la salsa.",
        "Cubre con queso manchego y gratina."
    )
)

/** Catálogo incluido en la app. Los nombres de los ingredientes son la llave de la sincronización con la estación. */
fun recetasBase(): List<RecetaUiData> {
    val originales = listOf(
            RecetaUiData(
                nombre = "Pasta al pomodoro",
                categoria = "COCINA ITALIANA • RECETA FAMILIAR",
                tiempo = "35 min",
                dificultad = "Dificultad media",
                porciones = "4 porciones",
                pasoActual = 3,
                totalPasos = 3,
                instruccion = "Pasa la pasta a la sartén, añade un poco del agua de cocción y mezcla energéticamente. Termina con albahaca y aceite de oliva.",
                chefTip = "Conserva una taza del agua de cocción. El almidón ayudará a que la salsa se agarre a la pasta.",
                ingredientes = listOf(
                    IngredientUi("1", "Spaghetti artesanal", "400 g", true),
                    IngredientUi("2", "Tomates maduros", "6", true),
                    IngredientUi("3", "Ajo fresco", "2 dientes", true),
                    IngredientUi("4", "Albahaca fresca", "1 manojo", false),
                    IngredientUi("5", "Aceite de oliva extra virgen", "45 ml", false),
                    IngredientUi("6", "Sal de mar y pimienta", "Al gusto", false)
                )
            ),
            RecetaUiData(
                nombre = "Enchiladas Suizas",
                categoria = "COCINA MEXICANA • TRADICIONAL",
                tiempo = "45 min",
                dificultad = "Dificultad alta",
                porciones = "3 porciones",
                pasoActual = 2,
                totalPasos = 4,
                instruccion = "Envuelve el pollo deshebrado en tortillas calientes, baña con la salsa verde cremosa a base de tomatillo y gratina con abundante queso manchego.",
                chefTip = "Usa crema ácida fresca justo al salir del horno para realzar la textura cremosa.",
                ingredientes = listOf(
                    IngredientUi("1", "Tortillas de maíz", "12 pzas", true),
                    IngredientUi("2", "Pollo cocido deshebrado", "500 g", true),
                    IngredientUi("3", "Tomatillo verde", "8 pzas", false),
                    IngredientUi("4", "Queso manchego rallado", "200 g", false)
                )
            )
    ).map { r -> r.copy(pasos = PASOS_ORIGINALES[r.nombre].orEmpty(), totalPasos = PASOS_ORIGINALES[r.nombre]?.size ?: r.totalPasos, pasoActual = 1) }

    val nuevas = listOf(
        RecetaUiData(
            nombre = "Guacamole", categoria = "COCINA MEXICANA • ENTRADA", tiempo = "10 min", dificultad = "Dificultad baja", porciones = "4 porciones",
            pasoActual = 1, totalPasos = 4, instruccion = "Pica finamente la cebolla, el jitomate, el cilantro y el chile.",
            chefTip = "Deja el hueso del aguacate dentro mientras lo guardas para que no se oscurezca.",
            ingredientes = listOf(
            IngredientUi("1", "Aguacates maduros", "3 pzas"),
            IngredientUi("2", "Jitomate", "1 pza"),
            IngredientUi("3", "Cebolla morada", "¼ pza"),
            IngredientUi("4", "Cilantro fresco", "1 manojo"),
            IngredientUi("5", "Limón", "2 pzas"),
            IngredientUi("6", "Chile serrano", "1 pza"),
            IngredientUi("7", "Sal", "Al gusto")
            ),
            pasos = listOf(
                "Pica finamente la cebolla, el jitomate, el cilantro y el chile.",
                "Machaca los aguacates con un tenedor dejando algunos trozos.",
                "Mezcla todo, agrega el jugo de limón y sal al gusto.",
                "Prueba, ajusta el picante y sirve de inmediato con totopos."
            )
        ),
        RecetaUiData(
            nombre = "Chilaquiles Verdes", categoria = "DESAYUNO • TRADICIONAL", tiempo = "25 min", dificultad = "Dificultad media", porciones = "2 porciones",
            pasoActual = 1, totalPasos = 5, instruccion = "Hierve los tomatillos con el chile durante 8 minutos.",
            chefTip = "Agrega los totopos al final: si hierven mucho quedan blandos.",
            ingredientes = listOf(
            IngredientUi("1", "Totopos", "150 g"),
            IngredientUi("2", "Tomatillo verde", "6 pzas"),
            IngredientUi("3", "Chile serrano", "2 pzas"),
            IngredientUi("4", "Cebolla", "¼ pza"),
            IngredientUi("5", "Cilantro", "1 manojo"),
            IngredientUi("6", "Crema", "3 cdas"),
            IngredientUi("7", "Queso fresco", "100 g"),
            IngredientUi("8", "Pollo deshebrado", "1 taza")
            ),
            pasos = listOf(
                "Hierve los tomatillos con el chile durante 8 minutos.",
                "Licúa con cebolla, cilantro y sal hasta lograr una salsa tersa.",
                "Calienta la salsa en una sartén a fuego bajo.",
                "Agrega los totopos y mezcla 1 minuto para que no se deshagan.",
                "Sirve con crema, queso y pollo."
            )
        ),
        RecetaUiData(
            nombre = "Arroz Rojo", categoria = "GUARNICIÓN • MEXICANA", tiempo = "40 min", dificultad = "Dificultad media", porciones = "4 porciones",
            pasoActual = 1, totalPasos = 5, instruccion = "Licúa el jitomate con el ajo y la cebolla.",
            chefTip = "No destapes el arroz mientras cuece: el vapor lo deja esponjoso.",
            ingredientes = listOf(
            IngredientUi("1", "Arroz", "1 taza"),
            IngredientUi("2", "Jitomate", "2 pzas"),
            IngredientUi("3", "Ajo", "1 diente"),
            IngredientUi("4", "Cebolla", "¼ pza"),
            IngredientUi("5", "Caldo de pollo", "2 tazas"),
            IngredientUi("6", "Aceite", "3 cdas"),
            IngredientUi("7", "Chícharos y zanahoria", "½ taza"),
            IngredientUi("8", "Sal", "Al gusto")
            ),
            pasos = listOf(
                "Licúa el jitomate con el ajo y la cebolla.",
                "Fríe el arroz en el aceite hasta que dore ligeramente.",
                "Agrega la salsa y deja que se consuma.",
                "Añade el caldo y las verduras, tapa y cocina a fuego bajo 18 minutos.",
                "Deja reposar 5 minutos y suelta el arroz con un tenedor."
            )
        ),
        RecetaUiData(
            nombre = "Sopa de Tortilla", categoria = "SOPAS • MEXICANA", tiempo = "35 min", dificultad = "Dificultad media", porciones = "4 porciones",
            pasoActual = 1, totalPasos = 5, instruccion = "Fríe las tortillas cortadas en tiras hasta que doren y escúrrelas.",
            chefTip = "Agrega las tortillas fritas al servir para que se mantengan crujientes.",
            ingredientes = listOf(
            IngredientUi("1", "Tortillas", "6 pzas"),
            IngredientUi("2", "Jitomate", "3 pzas"),
            IngredientUi("3", "Ajo", "2 dientes"),
            IngredientUi("4", "Cebolla", "¼ pza"),
            IngredientUi("5", "Caldo de pollo", "1 litro"),
            IngredientUi("6", "Chile pasilla", "2 pzas"),
            IngredientUi("7", "Aguacate", "1 pza"),
            IngredientUi("8", "Queso panela", "100 g"),
            IngredientUi("9", "Crema", "4 cdas")
            ),
            pasos = listOf(
                "Fríe las tortillas cortadas en tiras hasta que doren y escúrrelas.",
                "Licúa el jitomate con el ajo y la cebolla y fríelo en un poco de aceite.",
                "Agrega el caldo y hierve 15 minutos.",
                "Sirve el caldo caliente sobre las tiras de tortilla.",
                "Decora con aguacate, queso, crema y chile pasilla frito."
            )
        ),
        RecetaUiData(
            nombre = "Tacos de Pollo", categoria = "ANTOJITOS • MEXICANA", tiempo = "30 min", dificultad = "Dificultad baja", porciones = "3 porciones",
            pasoActual = 1, totalPasos = 5, instruccion = "Cuece la pechuga con sal durante 15 minutos y deshébrala.",
            chefTip = "Calienta las tortillas hasta que se inflen un poco: quedan más suaves.",
            ingredientes = listOf(
            IngredientUi("1", "Pechuga de pollo", "2 pzas"),
            IngredientUi("2", "Tortillas de maíz", "12 pzas"),
            IngredientUi("3", "Cebolla", "½ pza"),
            IngredientUi("4", "Cilantro", "1 manojo"),
            IngredientUi("5", "Limón", "2 pzas"),
            IngredientUi("6", "Salsa", "Al gusto"),
            IngredientUi("7", "Sal y pimienta", "Al gusto")
            ),
            pasos = listOf(
                "Cuece la pechuga con sal durante 15 minutos y deshébrala.",
                "Sazona con sal y pimienta y saltea 3 minutos.",
                "Calienta las tortillas en un comal.",
                "Arma los tacos con pollo, cebolla y cilantro.",
                "Sirve con limón y salsa."
            )
        ),
        RecetaUiData(
            nombre = "Hot Cakes Esponjosos", categoria = "DESAYUNO • DULCE", tiempo = "20 min", dificultad = "Dificultad baja", porciones = "3 porciones",
            pasoActual = 1, totalPasos = 5, instruccion = "Mezcla los ingredientes secos en un tazón.",
            chefTip = "No batas de más: la mezcla con algunos grumos da hot cakes más esponjosos.",
            ingredientes = listOf(
            IngredientUi("1", "Harina de trigo", "1½ tazas"),
            IngredientUi("2", "Leche", "1¼ tazas"),
            IngredientUi("3", "Huevo", "1 pza"),
            IngredientUi("4", "Mantequilla derretida", "3 cdas"),
            IngredientUi("5", "Polvo para hornear", "1 cda"),
            IngredientUi("6", "Azúcar", "2 cdas"),
            IngredientUi("7", "Vainilla", "1 cdta"),
            IngredientUi("8", "Sal", "1 pizca")
            ),
            pasos = listOf(
                "Mezcla los ingredientes secos en un tazón.",
                "Bate aparte la leche, el huevo, la mantequilla y la vainilla.",
                "Une ambas mezclas sin batir de más.",
                "Cuece porciones en una sartén caliente y voltea cuando salgan burbujas.",
                "Sirve con miel o fruta."
            )
        ),
        RecetaUiData(
            nombre = "Gelatina de Frutas", categoria = "POSTRES • FRESCO", tiempo = "20 min", dificultad = "Dificultad baja", porciones = "6 porciones",
            pasoActual = 1, totalPasos = 4, instruccion = "Disuelve la gelatina en el agua caliente.",
            chefTip = "Evita la piña fresca: impide que la gelatina cuaje correctamente.",
            ingredientes = listOf(
            IngredientUi("1", "Gelatina sabor neutro", "2 sobres"),
            IngredientUi("2", "Fruta mixta picada", "2 tazas"),
            IngredientUi("3", "Agua hirviendo", "1 litro")
            ),
            pasos = listOf(
                "Disuelve la gelatina en el agua caliente.",
                "Deja enfriar unos minutos.",
                "Vierte en el molde intercalando trozos de fruta.",
                "Refrigera por 3 horas hasta que cuaje."
            )
        )
    )
    return originales + nuevas
}

/** Receta del usuario (Firestore) a la forma que usa la pantalla. */
fun RecetaPropia.aUi(): RecetaUiData = RecetaUiData(
    nombre = nombre,
    categoria = categoria.uppercase().ifBlank { "MI RECETA" },
    tiempo = tiempo.ifBlank { "—" },
    dificultad = dificultad.ifBlank { "Dificultad media" },
    porciones = porciones.ifBlank { "—" },
    pasoActual = 1,
    totalPasos = maxOf(pasos.size, 1),
    instruccion = pasos.firstOrNull().orEmpty(),
    chefTip = chefTip.ifBlank { "Receta propia" },
    ingredientes = ingredientes.mapIndexed { i, (n, m) -> IngredientUi((i + 1).toString(), n, m) },
    pasos = pasos,
    propiaId = id
)

/** Receta completa que se manda a la estación: la PC y los demás celulares ven exactamente estos ingredientes. */
fun RecetaUiData.aJson(estados: List<IngredientUi>): JSONObject = JSONObject()
    .put("nombre", nombre).put("categoria", categoria).put("tiempo", tiempo)
    .put("dificultad", dificultad).put("porciones", porciones).put("chefTip", chefTip)
    .put("instruccion", if (pasos.isNotEmpty()) pasos.mapIndexed { i, p -> "${i + 1}. $p" }.joinToString("\n").take(800) else instruccion)
    .put("pasos", JSONArray(pasos.map { it.take(300) }.take(15)))
    .put("ingredientes", JSONArray(estados.take(40).map {
        JSONObject().put("nombre", it.nombre).put("medida", it.cantidad).put("listo", it.completado)
    }))

/** Receta que llegó desde la estación (la eligió otro celular y este no la tiene). */
fun recetaDesdeDetalle(d: JSONObject): RecetaUiData {
    val ings = d.optJSONArray("ingredientes") ?: JSONArray()
    val pasosJson = d.optJSONArray("pasos") ?: JSONArray()
    val pasos = (0 until pasosJson.length()).map { pasosJson.optString(it) }.filter { it.isNotBlank() }
    return RecetaUiData(
        nombre = d.optString("nombre"), categoria = d.optString("categoria").ifBlank { "RECETA COMPARTIDA" },
        tiempo = d.optString("tiempo").ifBlank { "—" }, dificultad = d.optString("dificultad").ifBlank { "—" },
        porciones = d.optString("porciones").ifBlank { "—" }, pasoActual = 1, totalPasos = maxOf(pasos.size, 1),
        instruccion = d.optString("instruccion").ifBlank { pasos.firstOrNull().orEmpty() },
        chefTip = d.optString("chefTip").ifBlank { "Receta compartida desde otro celular" },
        ingredientes = (0 until ings.length()).map {
            val o = ings.getJSONObject(it)
            IngredientUi((it + 1).toString(), o.optString("nombre"), o.optString("medida"), o.optBoolean("listo", false))
        },
        pasos = pasos
    )
}
