package com.recipestudio.kitchensync.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ingredientes de la receta abierta, para ofrecerlos en la pestaña Sustitutos. */
object SustitucionContexto {
    var ingredientes by mutableStateOf<List<String>>(emptyList())
}
