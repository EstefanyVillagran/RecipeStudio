package com.recipestudio.kitchensync.data.local

import android.content.Context
import java.io.File

/** Foto final de cada receta guardada en el almacenamiento propio de la app (no se borra como la caché). */
object ProgresoLocal {
    fun slug(receta: String): String = receta.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    private fun archivo(context: Context, receta: String) = File(context.filesDir, "foto_${slug(receta)}.jpg")

    /** Foto guardada de esa receta, o null si todavía no se terminó. */
    fun foto(context: Context, receta: String): File? = archivo(context, receta).takeIf { it.exists() && it.length() > 0 }

    fun guardarFoto(context: Context, receta: String, jpeg: ByteArray): File =
        archivo(context, receta).also { it.writeBytes(jpeg) }
}
