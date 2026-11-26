package com.recipestudio.kitchensync.data.remote

import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.recipestudio.kitchensync.data.local.ProgresoLocal
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Foto de una receta terminada en el muro. La imagen completa está en S3 ([key]); [thumbB64] es una miniatura ligera. */
class FotoMuro(
    val id: String, val receta: String, val autor: String, val key: String,
    val thumbB64: String, val creadoEn: Long, val ownerUid: String
)

/**
 * Muro de recetas (colección "muro"): lo que cada persona terminó, con su foto. Cualquiera con la app puede verlo;
 * solo el dueño puede publicar o borrar la suya (reglas de Firestore).
 */
object FirebaseMuro {
    private val db get() = FirebaseFirestore.getInstance()

    /** Un documento por persona y receta: volver a publicar reemplaza la foto en vez de duplicarla. */
    /** null si se publicó; si no, el motivo (para mostrarlo en pantalla en vez de fallar en silencio). */
    suspend fun publicar(receta: String, autor: String, key: String, miniaturaJpeg: ByteArray): String? = try {
        val uid = FirebaseReportes.asegurarSesion()
        val datos = hashMapOf<String, Any>(
            "receta" to receta.take(80), "autor" to autor.take(40), "key" to key,
            "thumbB64" to Base64.encodeToString(miniaturaJpeg, Base64.NO_WRAP),
            "creadoEn" to System.currentTimeMillis(), "ownerUid" to uid
        )
        db.collection("muro").document("${uid}_${ProgresoLocal.slug(receta)}").set(datos).await()
        null
    } catch (e: Exception) {
        Log.w("Muro", "No se pudo publicar: ${mensajeFirebase(e)}")
        mensajeFirebase(e)
    }

    fun fotos(limite: Long = 40): Flow<List<FotoMuro>> = callbackFlow {
        val reg = db.collection("muro").orderBy("creadoEn", Query.Direction.DESCENDING).limit(limite)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(FirebaseException(mensajeFirebase(err))); return@addSnapshotListener }
                trySend(snap?.documents?.map { d ->
                    FotoMuro(
                        id = d.id, receta = d.getString("receta").orEmpty(), autor = d.getString("autor").orEmpty(),
                        key = d.getString("key").orEmpty(), thumbB64 = d.getString("thumbB64").orEmpty(),
                        creadoEn = d.getLong("creadoEn") ?: 0L, ownerUid = d.getString("ownerUid").orEmpty()
                    )
                }.orEmpty())
            }
        awaitClose { reg.remove() }
    }

    suspend fun eliminar(id: String): Boolean = try {
        db.collection("muro").document(id).delete().await(); true
    } catch (e: Exception) {
        Log.w("Muro", "No se pudo eliminar: ${mensajeFirebase(e)}"); false
    }
}
