package com.recipestudio.kitchensync.data.remote

import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.recipestudio.kitchensync.data.local.ProgresoLocal
import kotlinx.coroutines.tasks.await

class AvanceNube(val hecha: Boolean, val completados: List<String>, val fotoJpeg: ByteArray?)

/**
 * Guarda en la nube las recetas terminadas del celular (progreso/{uid}/recetas/{receta}) para que
 * no se pierdan al desvincular la sesión, cambiar de estación o reinstalar la app mientras el
 * celular conserve su identidad. La foto va comprimida dentro del documento (máx. ~200 KB).
 */
object FirebaseProgreso {
    private val db get() = FirebaseFirestore.getInstance()

    private suspend fun ref(receta: String) =
        db.collection("progreso").document(FirebaseReportes.asegurarSesion())
            .collection("recetas").document(ProgresoLocal.slug(receta))

    /** Mejor esfuerzo: si no hay internet no interrumpe la cocina (la copia local ya quedó guardada). */
    suspend fun guardar(receta: String, hecha: Boolean, completados: List<String>, fotoJpeg: ByteArray?): Boolean = try {
        val datos = hashMapOf<String, Any>(
            "nombre" to receta,
            "hecha" to hecha,
            "completados" to completados,
            "actualizadoEn" to System.currentTimeMillis()
        )
        if (fotoJpeg != null && fotoJpeg.size <= 150_000) datos["fotoB64"] = Base64.encodeToString(fotoJpeg, Base64.NO_WRAP)
        ref(receta).set(datos, SetOptions.merge()).await()
        true
    } catch (e: Exception) {
        Log.w("Progreso", "No se pudo guardar en la nube: ${mensajeFirebase(e)}")
        false
    }

    suspend fun cargar(receta: String): AvanceNube? = try {
        val d = ref(receta).get().await()
        if (!d.exists()) null else {
            @Suppress("UNCHECKED_CAST")
            AvanceNube(
                hecha = d.getBoolean("hecha") ?: false,
                completados = (d.get("completados") as? List<String>).orEmpty(),
                fotoJpeg = d.getString("fotoB64")?.let { Base64.decode(it, Base64.NO_WRAP) }
            )
        }
    } catch (e: Exception) {
        Log.w("Progreso", "No se pudo leer de la nube: ${mensajeFirebase(e)}")
        null
    }
}
