package com.recipestudio.kitchensync.repository

import com.recipestudio.kitchensync.domain.SesionCocina

class SesionRepository {
    private var sesionActual: SesionCocina? = null

    fun crearSesionQR(): SesionCocina {
        val nuevaSesion = SesionCocina(
            sessionId = "SESSION-${(1000..9999).random()}",
            tokenSeguridad = "TOK-${System.currentTimeMillis()}",
            dispositivoMovilConectado = false
        )
        sesionActual = nuevaSesion
        return nuevaSesion
    }

    fun vincularDispositivoMovil(sessionIdScanned: String): Boolean {
        val sesion = sesionActual ?: return false
        if (sesion.sessionId != sessionIdScanned) return false
        sesion.confirmarConexion()
        return true
    }

    fun obtenerEstadoSesion(): SesionCocina? = sesionActual
}
