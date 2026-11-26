package com.recipestudio.kitchensync.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.recipestudio.kitchensync.data.remote.AgenteInfo
import com.recipestudio.kitchensync.data.remote.FirebaseException
import com.recipestudio.kitchensync.data.remote.FirebaseSoporte
import com.recipestudio.kitchensync.data.remote.PerfilSoporte
import com.recipestudio.kitchensync.data.remote.ReporteFs
import com.recipestudio.kitchensync.shared.hardware.BiometricAuth
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private enum class Pestana(val titulo: String, val estado: String, val vacio: String, val emoji: String) {
    NUEVOS("Nuevos", "Nuevo", "No hay reportes nuevos. ¡Todo al día!", "🎉"),
    PROCESO("En proceso", "En proceso", "No hay casos en proceso.", "☕"),
    RESUELTOS("Resueltos", "Resuelto", "Aún no hay casos resueltos.", "📂")
}

/** Panel de soporte (admin / agente): entra con su cuenta de Firebase y atiende los casos con un toque. */
@Composable
fun SoporteStaffScreen(onSalir: () -> Unit) {
    var perfil by remember { mutableStateOf<PerfilSoporte?>(null) }
    var revisando by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        perfil = FirebaseSoporte.perfilActual()
        revisando = false
    }
    Surface(Modifier.fillMaxSize(), color = Paleta.Crema) {
        when {
            revisando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Paleta.Naranja) }
            perfil == null -> LoginSoporte(onEntro = { perfil = it }, onSalir = onSalir)
            else -> PanelSoporte(perfil!!, onCerrarSesion = { FirebaseSoporte.cerrarSesion(); perfil = null }, onSalir = onSalir)
        }
    }
}

@Composable
private fun LoginSoporte(onEntro: (PerfilSoporte) -> Unit, onSalir: () -> Unit) {
    val scope = rememberCoroutineScope()
    var correo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var verClave by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(Paleta.Oscuro), contentAlignment = Alignment.Center) { Text("🛠", fontSize = 34.sp) }
        Spacer(Modifier.height(16.dp))
        Text("Panel de soporte", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Paleta.Texto)
        Text("Entra con tu cuenta de administrador o agente", color = Paleta.Gris, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = correo, onValueChange = { correo = it.take(80) }, label = { Text("Correo") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = clave, onValueChange = { clave = it.take(64) }, label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { verClave = !verClave }) { Text(if (verClave) "Ocultar" else "Ver", fontSize = 12.sp) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            enabled = !cargando && correo.isNotBlank() && clave.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = Paleta.Naranja),
            onClick = {
                cargando = true; error = null
                scope.launch {
                    try { onEntro(FirebaseSoporte.iniciarSesion(correo, clave)) }
                    catch (e: FirebaseException) { error = e.message }
                    finally { cargando = false }
                }
            }
        ) { Text(if (cargando) "Entrando…" else "Entrar", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        TextButton(onClick = onSalir) { Text("← Volver al escáner QR", color = Paleta.Gris) }
    }
}

@Composable
private fun PanelSoporte(perfil: PerfilSoporte, onCerrarSesion: () -> Unit, onSalir: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reportes by remember { mutableStateOf<List<ReporteFs>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var pestana by remember { mutableStateOf(Pestana.NUEVOS) }
    var busqueda by remember { mutableStateOf("") }
    var menuPerfil by remember { mutableStateOf(false) }
    var aResolver by remember { mutableStateOf<ReporteFs?>(null) }
    var aAsignar by remember { mutableStateOf<ReporteFs?>(null) }
    var aEliminar by remember { mutableStateOf<ReporteFs?>(null) }

    LaunchedEffect(Unit) {
        FirebaseSoporte.reportesParaPerfil(perfil)
            .catch { e -> error = e.message; cargando = false }
            .collect { reportes = it; error = null; cargando = false }
    }

    fun ejecutar(mensajeOk: String? = null, bloque: suspend () -> Unit) {
        scope.launch {
            try {
                bloque()
                if (mensajeOk != null) Toast.makeText(context, mensajeOk, Toast.LENGTH_SHORT).show()
            } catch (e: FirebaseException) {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    val q = busqueda.trim().lowercase()
    val delaPestana = reportes.filter { it.estado == pestana.estado }
        .filter { q.isBlank() || (it.titulo + it.id + it.dispositivo + it.categoria).lowercase().contains(q) }
        .sortedWith(compareByDescending<ReporteFs> { prioridadPeso(it.prioridad) }.thenByDescending { it.creadoEn })

    Column(Modifier.fillMaxSize()) {
        // Encabezado
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Soporte", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Paleta.Texto)
                Text(if (perfil.esAdmin) "Administrador · ve todos los casos" else "Agente · ve los casos asignados a ti", fontSize = 12.sp, color = Paleta.Gris)
            }
            Box {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Paleta.Oscuro).clickable { menuPerfil = true },
                    contentAlignment = Alignment.Center
                ) { Text(perfil.correo.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold) }
                DropdownMenu(expanded = menuPerfil, onDismissRequest = { menuPerfil = false }) {
                    DropdownMenuItem(text = { Text(perfil.correo, fontSize = 12.sp) }, onClick = {}, enabled = false)
                    DropdownMenuItem(text = { Text("📷 Volver al escáner QR") }, onClick = { menuPerfil = false; onSalir() })
                    DropdownMenuItem(text = { Text("🚪 Cerrar sesión") }, onClick = { menuPerfil = false; onCerrarSesion() })
                }
            }
        }

        OutlinedTextField(
            value = busqueda, onValueChange = { busqueda = it.take(40) }, singleLine = true,
            placeholder = { Text("Buscar por título, dispositivo o ID") }, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(8.dp))

        // Pestañas con contador: lo primero que se ve es cuánto hay por atender
        TabRow(selectedTabIndex = pestana.ordinal, containerColor = Paleta.Crema, contentColor = Paleta.Naranja) {
            Pestana.values().forEach { p ->
                val n = reportes.count { it.estado == p.estado }
                Tab(selected = p == pestana, onClick = { pestana = p }, text = { Text("${p.titulo} ($n)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) })
            }
        }

        error?.let {
            Card(Modifier.padding(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFBE0D9)), shape = RoundedCornerShape(14.dp)) {
                Text("No se pudieron cargar los reportes: $it", fontSize = 12.sp, color = Paleta.Texto, modifier = Modifier.padding(12.dp))
            }
        }

        when {
            cargando && error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Paleta.Naranja) }
            delaPestana.isEmpty() && error == null -> Column(
                Modifier.fillMaxSize().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(pestana.emoji, fontSize = 48.sp)
                Text(if (q.isBlank()) pestana.vacio else "Sin resultados para \"$busqueda\"", color = Paleta.Gris, fontSize = 14.sp)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(delaPestana, key = { it.id }) { rep ->
                    TarjetaCaso(
                        rep = rep, esAdmin = perfil.esAdmin,
                        onAtender = { ejecutar("Caso en proceso") { FirebaseSoporte.cambiarEstado(rep.id, "En proceso") } },
                        onResolver = { aResolver = rep },
                        onReabrir = { ejecutar("Caso reabierto") { FirebaseSoporte.cambiarEstado(rep.id, "En proceso") } },
                        onNota = { t -> ejecutar("Nota enviada") { FirebaseSoporte.agregarNota(rep.id, perfil.correo, t) } },
                        onAsignar = { aAsignar = rep },
                        onEliminar = { aEliminar = rep }
                    )
                }
            }
        }
    }

    // Resolver: una respuesta opcional para el usuario y listo
    aResolver?.let { rep ->
        var respuesta by remember(rep.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { aResolver = null },
            title = { Text("Resolver caso") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(rep.titulo, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = respuesta, onValueChange = { respuesta = it.take(300) }, minLines = 2,
                        label = { Text("Respuesta para el usuario (opcional)") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        aResolver = null
                        ejecutar("Caso resuelto ✔") {
                            if (respuesta.isNotBlank()) FirebaseSoporte.agregarNota(rep.id, perfil.correo, respuesta)
                            FirebaseSoporte.cambiarEstado(rep.id, "Resuelto")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Paleta.Verde)
                ) { Text("Marcar resuelto") }
            },
            dismissButton = { TextButton(onClick = { aResolver = null }) { Text("Cancelar") } }
        )
    }

    aAsignar?.let { rep ->
        var agentes by remember(rep.id) { mutableStateOf<List<AgenteInfo>?>(null) }
        var otro by remember(rep.id) { mutableStateOf(false) }
        var correoManual by remember(rep.id) { mutableStateOf("") }
        LaunchedEffect(rep.id) { agentes = FirebaseSoporte.agentes() }
        fun asignar(correo: String?, nombre: String) {
            aAsignar = null
            ejecutar(if (correo == null) "Asignación quitada" else "Asignado a $nombre") { FirebaseSoporte.asignar(rep.id, correo) }
        }
        AlertDialog(
            onDismissRequest = { aAsignar = null },
            title = { Text("Asignar caso") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(rep.titulo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    val lista = agentes
                    when {
                        lista == null -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Paleta.Naranja) }
                        lista.isEmpty() -> Text("Aún no hay agentes en la lista: aparecen cuando entran una vez al panel de soporte. Mientras tanto puedes escribir su correo.", fontSize = 12.sp, color = Paleta.Gris)
                        else -> lista.forEach { a -> OpcionAgente(a.nombre, a.correo, rep.asignadoA == a.correo) { asignar(a.correo, a.nombre) } }
                    }
                    OpcionAgente("Asignármelo a mí", perfil.correo, rep.asignadoA == perfil.correo.lowercase()) { asignar(perfil.correo.lowercase(), "ti") }
                    if (rep.asignadoA != null) TextButton(onClick = { asignar(null, "") }) { Text("Quitar asignación", color = Color(0xFFB3391B)) }
                    TextButton(onClick = { otro = !otro }) { Text(if (otro) "Ocultar" else "Escribir otro correo…", color = Paleta.Gris) }
                    if (otro) {
                        OutlinedTextField(value = correoManual, onValueChange = { correoManual = it.take(60) }, singleLine = true, label = { Text("Correo del agente") })
                        Button(enabled = correoManual.contains("@"), onClick = { asignar(correoManual.trim().lowercase(), correoManual.trim()) }) { Text("Asignar") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { aAsignar = null }) { Text("Cancelar") } }
        )
    }

    aEliminar?.let { rep ->
        AlertDialog(
            onDismissRequest = { aEliminar = null },
            title = { Text("Eliminar reporte") },
            text = { Text("¿Eliminar \"${rep.titulo}\"? No se puede deshacer. Se pedirá tu huella o rostro para confirmar.") },
            confirmButton = {
                TextButton(onClick = {
                    aEliminar = null
                    val activity = context as? FragmentActivity
                    if (activity == null) {
                        Toast.makeText(context, "Biometría no disponible en esta vista", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    BiometricAuth(activity).autenticarChef(
                        onExito = { ejecutar("Reporte eliminado") { FirebaseSoporte.eliminar(rep.id) } },
                        onError = { m -> Toast.makeText(context, m, Toast.LENGTH_SHORT).show() }
                    )
                }) { Text("Eliminar", color = Color(0xFFB3391B)) }
            },
            dismissButton = { TextButton(onClick = { aEliminar = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun OpcionAgente(nombre: String, correo: String, seleccionado: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (seleccionado) Color(0xFFFBE0D9) else Color(0xFFF5F0E8))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Paleta.Oscuro), contentAlignment = Alignment.Center) {
                Text(nombre.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(nombre, fontWeight = FontWeight.SemiBold, color = Paleta.Texto, fontSize = 14.sp)
                Text(correo, fontSize = 11.sp, color = Paleta.Gris)
            }
            if (seleccionado) Text("✓", color = Paleta.Naranja, fontWeight = FontWeight.Bold)
        }
    }
}

private fun prioridadPeso(p: String) = when (p) { "Alta" -> 3; "Media" -> 2; else -> 1 }

@Composable
private fun TarjetaCaso(
    rep: ReporteFs, esAdmin: Boolean,
    onAtender: () -> Unit, onResolver: () -> Unit, onReabrir: () -> Unit,
    onNota: (String) -> Unit, onAsignar: () -> Unit, onEliminar: () -> Unit
) {
    val colorPrioridad = when (rep.prioridad) { "Alta" -> Color(0xFFB3391B); "Media" -> Paleta.Amarillo; else -> Paleta.Verde }
    var abierta by remember(rep.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var nota by remember(rep.id) { mutableStateOf("") }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorPrioridad))
                Text(rep.titulo, fontWeight = FontWeight.Bold, color = Paleta.Texto, modifier = Modifier.weight(1f))
                Text(haceTiempo(rep.creadoEn), fontSize = 11.sp, color = Paleta.Gris)
            }
            Text("${rep.categoria} · Prioridad ${rep.prioridad} · ${rep.dispositivo}", fontSize = 11.sp, color = Paleta.Gris)
            Text(rep.descripcion, fontSize = 14.sp, color = Paleta.Texto, maxLines = if (abierta) Int.MAX_VALUE else 3)
            rep.asignadoA?.let { Text("👤 $it", fontSize = 12.sp, color = Paleta.Naranja, fontWeight = FontWeight.SemiBold) }

            if (abierta) {
                rep.notas.forEach {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F2EA)), shape = RoundedCornerShape(12.dp)) {
                        Text("📝 ${it.autor}: ${it.texto}", fontSize = 12.sp, color = Paleta.Texto, modifier = Modifier.padding(10.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = nota, onValueChange = { nota = it.take(300) }, singleLine = true,
                        placeholder = { Text("Escribir nota…", fontSize = 13.sp) }, modifier = Modifier.weight(1f)
                    )
                    Button(enabled = nota.isNotBlank(), onClick = { onNota(nota); nota = "" }, colors = ButtonDefaults.buttonColors(containerColor = Paleta.Oscuro)) { Text("Enviar") }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Una sola acción principal según el estado
                when (rep.estado) {
                    "Nuevo" -> Button(onClick = onAtender, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Paleta.Naranja)) { Text("Atender") }
                    "En proceso" -> Button(onClick = onResolver, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Paleta.Verde)) { Text("Resolver") }
                    else -> OutlinedButton(onClick = onReabrir, shape = RoundedCornerShape(12.dp)) { Text("Reabrir") }
                }
                TextButton(onClick = { abierta = !abierta }) { Text(if (abierta) "Ocultar" else "Detalles", color = Paleta.Gris) }
                Spacer(Modifier.weight(1f))
                if (esAdmin) {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 20.sp, color = Paleta.Gris) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("👤 Asignar a un agente") }, onClick = { menu = false; onAsignar() })
                            DropdownMenuItem(text = { Text("🗑 Eliminar") }, onClick = { menu = false; onEliminar() })
                        }
                    }
                }
            }
        }
    }
}
