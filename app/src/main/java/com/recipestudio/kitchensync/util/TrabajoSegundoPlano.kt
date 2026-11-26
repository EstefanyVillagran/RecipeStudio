package com.recipestudio.kitchensync.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Trabajo que debe terminar aunque la persona cambie de pestaña o la pantalla se recomponga (subir la foto final,
 * publicarla en el muro). Un LaunchedEffect NO sirve para esto: se cancela cuando cambia su clave o sale de la composición.
 */
object TrabajoSegundoPlano {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
