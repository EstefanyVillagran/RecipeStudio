package com.recipestudio.kitchensync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recipestudio.kitchensync.presentation.ui.RecipeStudioScreen

/** Raíz de la app: sin cuentas ni contraseñas. El celular entra con el QR (pestaña Receta). */
@Composable
fun AppRoot() {
    MainShell()
}

@Composable
fun MainShell() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var modoSoporte by rememberSaveable { mutableStateOf(false) }

    // Pantalla de soporte (admin/agente): sin barra inferior, se entra desde el escáner QR.
    if (modoSoporte) {
        SoporteStaffScreen(onSalir = { modoSoporte = false })
        return
    }
    val items = listOf(
        "Receta" to Icons.Default.Restaurant,
        "Sustitutos" to Icons.Default.SwapHoriz,
        "Chat" to Icons.Default.Forum,
        "Reportar" to Icons.Default.SupportAgent
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, (nombre, icono) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icono, contentDescription = nombre) },
                        label = { Text(nombre, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> RecipeStudioScreen(onSoporte = { modoSoporte = true }, onIrSustitutos = { tab = 1 })
                1 -> SustitucionesScreen()
                2 -> EstacionesScreen()
                else -> SoporteScreen()
            }
        }
    }
}
