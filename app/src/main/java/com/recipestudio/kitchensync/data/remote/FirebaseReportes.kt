package com.recipestudio.kitchensync.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class NotaFs(val autor: String, val texto: String, val hora: Long)

/** Reporte de soporte guardado en Firestore (colección "reportes"). */
data class ReporteFs(
    val id: String,
    val titulo: String,
    val descripcion: String,
    val categoria: String,
    val prioridad: String,
    val estado: String,
    val dispositivo: String,
    val asignadoA: String?,
    val creadoEn: Long,
    val notas: List<NotaFs> = emptyList()
)

/** Datos de quien entró al panel de soporte (documento usuarios/{uid}). */
data class PerfilSoporte(val uid: String, val correo: String, val rol: String) {
    val esAdmin get() = rol == "admin"
}

class FirebaseException(mensaje: String) : Exception(mensaje)

/** Traduce un error de Firebase a un texto útil para el usuario, con el código para poder depurar. */
internal fun mensajeFirebase(e: Throwable): String = when (e) {
    is FirebaseException -> e.message ?: "Error de Firebase"
    is FirebaseNetworkException -> "Sin conexión a internet"
    is FirebaseAuthException -> when (e.errorCode) {
        "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> "Correo o contraseña incorrectos"
        "ERROR_INVALID_EMAIL" -> "El correo no es válido"
        "ERROR_USER_DISABLED" -> "Esta cuenta está deshabilitada"
        "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos, espera un momento"
        "ERROR_OPERATION_NOT_ALLOWED" -> "Activa este método de acceso en Firebase > Authentication (${e.errorCode})"
        else -> "Error de autenticación (${e.errorCode})"
    }
    is FirebaseFirestoreException -> when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Firestore rechazó la operación (PERMISSION_DENIED): revisa que publicaste las reglas y que activaste Auth Anónimo"
        FirebaseFirestoreException.Code.UNAVAILABLE -> "Sin conexión con Firestore (UNAVAILABLE)"
        FirebaseFirestoreException.Code.FAILED_PRECONDITION -> "Firestore pide un índice o la base no está creada (FAILED_PRECONDITION)"
        else -> "Error de Firestore (${e.code})"
    }
    else -> "Error: ${e.message ?: e.javaClass.simpleName}"
}

private fun leerReporte(d: com.google.firebase.firestore.DocumentSnapshot): ReporteFs {
    @Suppress("UNCHECKED_CAST")
    val notas = (d.get("notas") as? List<Map<String, Any?>>).orEmpty().map {
        NotaFs(it["autor"] as? String ?: "", it["texto"] as? String ?: "", (it["hora"] as? Number)?.toLong() ?: 0L)
    }
    return ReporteFs(
        id = d.id,
        titulo = d.getString("titulo").orEmpty(),
        descripcion = d.getString("descripcion").orEmpty(),
        categoria = d.getString("categoria").orEmpty(),
        prioridad = d.getString("prioridad").orEmpty(),
        estado = d.getString("estado").orEmpty(),
        dispositivo = d.getString("dispositivo").orEmpty(),
        asignadoA = d.getString("asignadoA"),
        creadoEn = d.getLong("creadoEn") ?: 0L,
        notas = notas
    )
}

/**
 * Lado del CELULAR. No tiene cuenta: Auth anónimo (uid propio). Las reglas hacen que solo lea y
 * cree SUS reportes.
 */
object FirebaseReportes {
    private const val COLECCION = "reportes"
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    suspend fun asegurarSesion(): String {
        auth.currentUser?.let { return it.uid }
        try {
            return auth.signInAnonymously().await().user?.uid
                ?: throw FirebaseException("No se pudo iniciar la sesión anónima")
        } catch (e: Exception) {
            throw FirebaseException(mensajeFirebase(e))
        }
    }

    suspend fun crear(titulo: String, descripcion: String, categoria: String, prioridad: String, dispositivo: String): String {
        val uid = asegurarSesion()
        val id = "REP-" + System.currentTimeMillis()
        val datos = hashMapOf(
            "titulo" to titulo.trim(),
            "descripcion" to descripcion.trim(),
            "categoria" to categoria,
            "prioridad" to prioridad,
            "estado" to "Nuevo",
            "dispositivo" to dispositivo,
            "ownerUid" to uid,
            "asignadoA" to null,
            "creadoEn" to System.currentTimeMillis()
        )
        try {
            db.collection(COLECCION).document(id).set(datos).await()
            return id
        } catch (e: Exception) {
            throw FirebaseException(mensajeFirebase(e))
        }
    }

    /** Tiempo real: mis reportes (con las notas que deja el soporte). */
    fun misReportes(uid: String): Flow<List<ReporteFs>> = callbackFlow {
        val reg = db.collection(COLECCION)
            .whereEqualTo("ownerUid", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(FirebaseException(mensajeFirebase(err)))
                    return@addSnapshotListener
                }
                // se ordena aquí para no necesitar un índice compuesto en Firestore
                trySend(snap?.documents?.map(::leerReporte).orEmpty().sortedByDescending { it.creadoEn })
            }
        awaitClose { reg.remove() }
    }
}

/**
 * Lado del SOPORTE (admin / agente). Usa una segunda instancia de Firebase ("soporte") para que
 * entrar al panel NO reemplace la identidad anónima del celular (y no se pierdan sus reportes).
 */
object FirebaseSoporte {
    private var app: FirebaseApp? = null

    fun iniciar(context: Context) {
        if (app != null) return
        app = try {
            FirebaseApp.getInstance("soporte")
        } catch (e: IllegalStateException) {
            FirebaseApp.initializeApp(context.applicationContext, FirebaseApp.getInstance().options, "soporte")
        }
    }

    private val auth get() = FirebaseAuth.getInstance(requireNotNull(app) { "FirebaseSoporte.iniciar() no se llamó" })
    private val db get() = FirebaseFirestore.getInstance(requireNotNull(app) { "FirebaseSoporte.iniciar() no se llamó" })

    private suspend fun leerPerfil(uid: String, correo: String): PerfilSoporte? {
        val doc = db.collection("usuarios").document(uid).get().await()
        val rol = doc.getString("rol")
        return if (rol == "admin" || rol == "agente") PerfilSoporte(uid, correo, rol) else null
    }

    /** Perfil de la sesión que ya estaba abierta (Firebase la recuerda), o null. */
    suspend fun perfilActual(): PerfilSoporte? {
        val u = auth.currentUser ?: return null
        return try { leerPerfil(u.uid, u.email.orEmpty()) } catch (e: Exception) { null }
    }

    suspend fun iniciarSesion(correo: String, clave: String): PerfilSoporte {
        try {
            val user = auth.signInWithEmailAndPassword(correo.trim(), clave).await().user
                ?: throw FirebaseException("No se pudo iniciar sesión")
            val perfil = leerPerfil(user.uid, user.email.orEmpty())
            if (perfil == null) {
                auth.signOut()
                throw FirebaseException(
                    "La cuenta entró, pero no tiene rol. Crea el documento usuarios/${user.uid} con el campo rol = admin o agente"
                )
            }
            return perfil
        } catch (e: FirebaseException) {
            throw e
        } catch (e: Exception) {
            throw FirebaseException(mensajeFirebase(e))
        }
    }

    fun cerrarSesion() = auth.signOut()

    fun todosLosReportes(): Flow<List<ReporteFs>> = callbackFlow {
        val reg = db.collection("reportes").addSnapshotListener { snap, err ->
            if (err != null) {
                close(FirebaseException(mensajeFirebase(err)))
                return@addSnapshotListener
            }
            trySend(snap?.documents?.map(::leerReporte).orEmpty().sortedByDescending { it.creadoEn })
        }
        awaitClose { reg.remove() }
    }

    fun reportesParaPerfil(perfil: PerfilSoporte): Flow<List<ReporteFs>> = callbackFlow {
        val query = if (perfil.esAdmin) {
            db.collection("reportes")
        } else {
            db.collection("reportes").whereEqualTo("asignadoA", perfil.correo)
        }
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) {
                close(FirebaseException(mensajeFirebase(err)))
                return@addSnapshotListener
            }
            trySend(snap?.documents?.map(::leerReporte).orEmpty().sortedByDescending { it.creadoEn })
        }
        awaitClose { reg.remove() }
    }

    private suspend fun actualizar(id: String, campos: Map<String, Any?>) {
        try {
            db.collection("reportes").document(id).update(campos).await()
        } catch (e: Exception) {
            throw FirebaseException(mensajeFirebase(e))
        }
    }

    suspend fun cambiarEstado(id: String, estado: String) = actualizar(id, mapOf("estado" to estado))

    suspend fun asignar(id: String, agente: String?) = actualizar(id, mapOf("asignadoA" to agente?.trim()?.ifBlank { null }))

    suspend fun agregarNota(id: String, autor: String, texto: String) {
        val nota = mapOf("autor" to autor, "texto" to texto.trim(), "hora" to System.currentTimeMillis())
        actualizar(id, mapOf("notas" to FieldValue.arrayUnion(nota)))
    }

    suspend fun eliminar(id: String) {
        try {
            db.collection("reportes").document(id).delete().await()
        } catch (e: Exception) {
            throw FirebaseException(mensajeFirebase(e))
        }
    }
}
