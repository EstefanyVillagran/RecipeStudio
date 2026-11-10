package com.recipestudio.kitchensync.domain

data class SesionCocina(
    val sessionId: String,
    val tokenSeguridad: String,
    var dispositivoMovilConectado: Boolean = false
) {
    fun confirmarConexion() {
        dispositivoMovilConectado = true
    }
}
