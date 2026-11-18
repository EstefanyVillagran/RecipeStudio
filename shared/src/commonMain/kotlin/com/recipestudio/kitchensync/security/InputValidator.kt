package com.recipestudio.kitchensync.security

/** Resultado de validar una entrada del usuario. Los mensajes son genéricos: no revelan datos sensibles. */
sealed class Validacion {
    object Ok : Validacion()
    data class Error(val mensaje: String) : Validacion()

    val esValida: Boolean get() = this is Ok
}

/**
 * Validaciones del lado del cliente (Práctica 5). Mejoran la experiencia, pero NO sustituyen
 * a las del servidor, que vuelve a validar todo (la validación del cliente puede saltarse).
 */
object InputValidator {
    const val MAX_USUARIO = 30
    const val MIN_PASSWORD = 8
    const val MAX_PASSWORD = 64
    const val MAX_MENSAJE = 280
    const val MIN_TITULO = 3
    const val MAX_TITULO = 80
    const val MIN_DESCRIPCION = 5
    const val MAX_DESCRIPCION = 500

    private val usuarioPermitido = Regex("^[A-Za-z0-9._-]+$")
    private val urlServidor = Regex("^https?://[A-Za-z0-9.-]+(:\\d{2,5})?$")

    fun validarUsuario(usuario: String): Validacion = when {
        usuario.isBlank() -> Validacion.Error("Escribe tu usuario")
        usuario.length > MAX_USUARIO -> Validacion.Error("El usuario es demasiado largo")
        !usuarioPermitido.matches(usuario.trim()) -> Validacion.Error("El usuario contiene caracteres no permitidos")
        else -> Validacion.Ok
    }

    fun validarPassword(password: String): Validacion = when {
        password.isEmpty() -> Validacion.Error("Escribe tu contraseña")
        password.length < MIN_PASSWORD -> Validacion.Error("La contraseña debe tener al menos $MIN_PASSWORD caracteres")
        password.length > MAX_PASSWORD -> Validacion.Error("La contraseña es demasiado larga")
        else -> Validacion.Ok
    }

    fun validarMensaje(texto: String): Validacion = when {
        texto.isBlank() -> Validacion.Error("No se pueden enviar mensajes vacíos")
        texto.trim().length > MAX_MENSAJE -> Validacion.Error("Máximo $MAX_MENSAJE caracteres")
        else -> Validacion.Ok
    }

    fun validarReporte(titulo: String, descripcion: String): Validacion = when {
        titulo.isBlank() || descripcion.isBlank() -> Validacion.Error("Completa el título y la descripción")
        titulo.trim().length !in MIN_TITULO..MAX_TITULO -> Validacion.Error("El título debe tener entre $MIN_TITULO y $MAX_TITULO caracteres")
        descripcion.trim().length !in MIN_DESCRIPCION..MAX_DESCRIPCION -> Validacion.Error("La descripción debe tener entre $MIN_DESCRIPCION y $MAX_DESCRIPCION caracteres")
        else -> Validacion.Ok
    }

    fun validarCantidad(texto: String): Validacion {
        val n = texto.trim().replace(',', '.').toDoubleOrNull()
        return when {
            n == null -> Validacion.Error("Escribe una cantidad numérica")
            n <= 0.0 -> Validacion.Error("La cantidad debe ser mayor que cero")
            n > 100000.0 -> Validacion.Error("La cantidad es demasiado grande")
            else -> Validacion.Ok
        }
    }

    fun validarUrlServidor(url: String): Validacion =
        if (urlServidor.matches(url.trim().trimEnd('/'))) Validacion.Ok
        else Validacion.Error("Dirección inválida. Ejemplo: http://192.168.1.10:8080")
}
