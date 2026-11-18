package com.recipestudio.kitchensync.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Receta creada por el usuario. Vive en la nube bajo su identidad (recetasPropias/{uid}/items/{id}). */
data class RecetaPropia(
    val id: String = "",
    val nombre: String,
    val categoria: String = "",
    val tiempo: String = "",
    val dificultad: String = "",
    val porciones: String = "",
    val chefTip: String = "",
    val pasos: List<String> = emptyList(),
    val ingredientes: List<Pair<String, String>> = emptyList(),   // nombre a cantidad
    val creadoEn: Long = 0L
)

object FirebaseRecetas {
    private val db get() = FirebaseFirestore.getInstance()
    private fun items(uid: String) = db.collection("recetasPropias").document(uid).collection("items")

    fun misRecetas(uid: String): Flow<List<RecetaPropia>> = callbackFlow {
        val reg = items(uid).addSnapshotListener { snap, err ->
            if (err != null) { close(FirebaseException(mensajeFirebase(err))); return@addSnapshotListener }
            val lista = snap?.documents?.map { d ->
                @Suppress("UNCHECKED_CAST")
                val ings = (d.get("ingredientes") as? List<Map<String, Any?>>).orEmpty().map {
                    (it["nombre"] as? String ?: "") to (it["medida"] as? String ?: "")
                }
                @Suppress("UNCHECKED_CAST")
                val pasos = (d.get("pasos") as? List<String>).orEmpty()
                RecetaPropia(
                    id = d.id, nombre = d.getString("nombre").orEmpty(), categoria = d.getString("categoria").orEmpty(),
                    tiempo = d.getString("tiempo").orEmpty(), dificultad = d.getString("dificultad").orEmpty(),
                    porciones = d.getString("porciones").orEmpty(), chefTip = d.getString("chefTip").orEmpty(),
                    pasos = pasos, ingredientes = ings, creadoEn = d.getLong("creadoEn") ?: 0L
                )
            }.orEmpty().sortedByDescending { it.creadoEn }
            trySend(lista)
        }
        awaitClose { reg.remove() }
    }

    /** Crea o actualiza (si [RecetaPropia.id] ya existe). Devuelve true si se guardó. */
    suspend fun guardar(r: RecetaPropia): Boolean = try {
        val uid = FirebaseReportes.asegurarSesion()
        val id = r.id.ifBlank { "R" + System.currentTimeMillis() }
        val datos = hashMapOf<String, Any>(
            "nombre" to r.nombre.trim().take(60), "categoria" to r.categoria.trim().take(40),
            "tiempo" to r.tiempo.trim().take(20), "dificultad" to r.dificultad.trim().take(30),
            "porciones" to r.porciones.trim().take(20), "chefTip" to r.chefTip.trim().take(300),
            "pasos" to r.pasos.map { it.trim().take(300) }.filter { it.isNotBlank() }.take(15),
            "ingredientes" to r.ingredientes.filter { it.first.isNotBlank() }.take(40)
                .map { mapOf("nombre" to it.first.trim().take(80), "medida" to it.second.trim().take(30)) },
            "creadoEn" to (if (r.creadoEn > 0) r.creadoEn else System.currentTimeMillis())
        )
        items(uid).document(id).set(datos).await()
        true
    } catch (e: Exception) {
        Log.w("Recetas", "No se pudo guardar: ${mensajeFirebase(e)}")
        false
    }

    suspend fun eliminar(id: String): Boolean = try {
        items(FirebaseReportes.asegurarSesion()).document(id).delete().await(); true
    } catch (e: Exception) {
        Log.w("Recetas", "No se pudo eliminar: ${mensajeFirebase(e)}"); false
    }
}
