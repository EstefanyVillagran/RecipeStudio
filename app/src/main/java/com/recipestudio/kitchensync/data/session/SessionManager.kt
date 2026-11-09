package com.recipestudio.kitchensync.data.session

object SessionManager {
    var usuarioId: String? = null
        private set
    var nombreChef: String? = null
        private set
    var rol: String? = null
        private set

    fun iniciarSesion(id: String, nombre: String, rolUsuario: String) {
        usuarioId = id
        nombreChef = nombre
        rol = rolUsuario
    }

    fun cerrarSesion() {
        usuarioId = null
        nombreChef = null
        rol = null
    }

    fun haySesionActiva(): Boolean = usuarioId != null
}
