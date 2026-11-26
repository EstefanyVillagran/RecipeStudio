package com.recipestudio.kitchensync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recipestudio.kitchensync.presentation.ui.RecipeStudioScreen

/** Raíz de la app: sin cuentas ni contraseñas. El celular entra con el QR (pestaña Receta). */
@Composable
fun AppRoot() {
    // Si la persona tiene la letra del sistema muy grande (Ajustes > Pantalla > Tamaño de fuente), la app la respeta hasta
    // un tope de 1.15x: más que eso desacomoda botones y tarjetas. El resto del diseño se mantiene igual.
    val densidad = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(densidad.density, fontScale = densidad.fontScale.coerceAtMost(1.15f))) {
        MainShell()
    }
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
        "Muro" to Icons.Default.PhotoLibrary,
        "Ayuda" to Icons.Default.SupportAgent
    )

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White) {
                items.forEachIndexed { i, (nombre, icono) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icono, contentDescription = nombre) },
                        label = { Text(nombre, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Paleta.Naranja, selectedTextColor = Paleta.Naranja,
                            indicatorColor = Paleta.Tarjeta, unselectedIconColor = Paleta.Gris, unselectedTextColor = Paleta.Gris
                        )
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
                3 -> MuroScreen()
                else -> SoporteScreen()
            }
        }
    }
}
