package com.recipestudio.kitchensync.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recipestudio.kitchensync.data.remote.FirebaseException
import com.recipestudio.kitchensync.data.remote.FirebaseSoporte
import com.recipestudio.kitchensync.data.remote.PerfilSoporte
import com.recipestudio.kitchensync.data.remote.ReporteFs
import com.recipestudio.kitchensync.shared.hardware.BiometricAuth
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ESTADOS = listOf("Nuevo", "En proceso", "Resuelto")

/** Entrada al panel de soporte desde el celular: login con la cuenta de Firebase y gestión de reportes. */
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
            else -> PanelSoporte(
                perfil = perfil!!,
                onCerrarSesion = { FirebaseSoporte.cerrarSesion(); perfil = null },
                onSalir = onSalir
            )
        }
    }
}

@Composable
private fun LoginSoporte(onEntro: (PerfilSoporte) -> Unit, onSalir: () -> Unit) {
    val scope = rememberCoroutineScope()
    var correo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(70.dp).clip(RoundedCornerShape(20.dp)).background(Paleta.Oscuro), contentAlignment = Alignment.Center) {
            Text("🛠", fontSize = 32.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("Panel de soporte", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Paleta.Texto)
        Text("Entra con tu cuenta de administrador o agente", color = Paleta.Gris, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = correo, onValueChange = { correo = it.take(80) }, label = { Text("Correo") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = clave, onValueChange = { clave = it.take(64) }, label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(14.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !cargando && correo.isNotBlank() && clave.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = Paleta.Naranja),
            onClick = {
                cargando = true
                error = null
                scope.launch {
                    try {
                        onEntro(FirebaseSoporte.iniciarSesion(correo, clave))
                    } catch (e: FirebaseException) {
                        error = e.message
                    } finally {
                        cargando = false
                    }
                }
            }
        ) { Text(if (cargando) "Entrando…" else "Entrar", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onSalir) { Text("← Volver al escáner QR", color = Paleta.Gris) }
    }
}

@Composable
private fun PanelSoporte(perfil: PerfilSoporte, onCerrarSesion: () -> Unit, onSalir: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reportes by remember { mutableStateOf<List<ReporteFs>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var filtro by remember { mutableStateOf("Todos") }
    var abierto by remember { mutableStateOf<String?>(null) }
    var aEliminar by remember { mutableStateOf<ReporteFs?>(null) }

    LaunchedEffect(perfil.uid, perfil.rol, perfil.correo) {
        FirebaseSoporte.reportesParaPerfil(perfil)
            .catch { e -> error = e.message }
            .collect { reportes = it; error = null }
    }

    fun ejecutar(bloque: suspend () -> Unit) {
        scope.launch {
            try { bloque() } catch (e: FirebaseException) { Toast.makeText(context, e.message, Toast.LENGTH_LONG).show() }
        }
    }

    val visibles = if (filtro == "Todos") reportes else reportes.filter { it.estado == filtro }
    val tituloRol = if (perfil.esAdmin) "Administrador" else "Agente asignado"

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("🛠 Soporte", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Paleta.Texto)
                Text("${perfil.correo} · $tituloRol", fontSize = 11.sp, color = Paleta.Gris)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSalir) { Text("QR") }
                Button(
                    onClick = onCerrarSesion,
                    colors = ButtonDefaults.buttonColors(containerColor = Paleta.Oscuro),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cerrar sesión")
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Contador("Total", reportes.size, Paleta.Oscuro, Modifier.weight(1f))
            Contador("Nuevos", reportes.count { it.estado == "Nuevo" }, Paleta.Naranja, Modifier.weight(1f))
            Contador("En proceso", reportes.count { it.estado == "En proceso" }, Paleta.Amarillo, Modifier.weight(1f))
            Contador("Resueltos", reportes.count { it.estado == "Resuelto" }, Paleta.Verde, Modifier.weight(1f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("Todos") + ESTADOS).forEach { f ->
                FilterChip(selected = f == filtro, onClick = { filtro = f }, label = { Text(f, fontSize = 12.sp) })
            }
        }
        error?.let {
            Card(Modifier.padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFBE0D9))) {
                Text("No se pudieron cargar los reportes: $it", fontSize = 12.sp, color = Paleta.Texto, modifier = Modifier.padding(12.dp))
            }
        }
        if (visibles.isEmpty() && error == null) {
            Text("No hay reportes en esta vista.", color = Paleta.Gris, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visibles, key = { it.id }) { rep ->
                TarjetaReporte(
                    rep = rep,
                    esAdmin = perfil.esAdmin,
                    expandida = abierto == rep.id,
                    onToggle = { abierto = if (abierto == rep.id) null else rep.id },
                    onEstado = { e -> ejecutar { FirebaseSoporte.cambiarEstado(rep.id, e) } },
                    onAsignar = { a -> ejecutar { FirebaseSoporte.asignar(rep.id, a) } },
                    onNota = { t -> ejecutar { FirebaseSoporte.agregarNota(rep.id, perfil.correo, t) } },
                    onEliminar = { aEliminar = rep }
                )
            }
        }
    }

    aEliminar?.let { rep ->
        AlertDialog(
            onDismissRequest = { aEliminar = null },
            title = { Text("Eliminar reporte") },
            text = { Text("¿Eliminar ${rep.id} (${rep.titulo})? No se puede deshacer. Se pedirá tu huella o rostro para confirmar.") },
            confirmButton = {
                TextButton(onClick = {
                    aEliminar = null
                    val activity = context as? FragmentActivity
                    if (activity == null) {
                        Toast.makeText(context, "Biometría no disponible en esta vista", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    // Práctica de biometría: eliminar datos exige huella o rostro
                    BiometricAuth(activity).autenticarChef(
                        onExito = { ejecutar { FirebaseSoporte.eliminar(rep.id) } },
                        onError = { m -> Toast.makeText(context, m, Toast.LENGTH_SHORT).show() }
                    )
                }) { Text("Eliminar", color = Paleta.Naranja) }
            },
            dismissButton = { TextButton(onClick = { aEliminar = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun Contador(titulo: String, valor: Int, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valor.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Text(titulo, fontSize = 9.sp, color = Paleta.Gris)
        }
    }
}

@Composable
private fun TarjetaReporte(
    rep: ReporteFs, esAdmin: Boolean, expandida: Boolean,
    onToggle: () -> Unit, onEstado: (String) -> Unit, onAsignar: (String?) -> Unit,
    onNota: (String) -> Unit, onEliminar: () -> Unit
) {
    val colorEstado = when (rep.estado) { "Resuelto" -> Paleta.Verde; "En proceso" -> Paleta.Amarillo; else -> Paleta.Naranja }
    val colorPrioridad = when (rep.prioridad) { "Alta" -> Color(0xFFB3391B); "Media" -> Paleta.Amarillo; else -> Paleta.Gris }
    val fecha = remember(rep.creadoEn) { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(rep.creadoEn)) }
    var asignado by remember(rep.id, rep.asignadoA) { mutableStateOf(rep.asignadoA.orEmpty()) }
    var nota by remember(rep.id) { mutableStateOf("") }

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.clickable { onToggle() }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorEstado))
                Text(rep.titulo, fontWeight = FontWeight.Bold, color = Paleta.Texto, modifier = Modifier.weight(1f))
                Text(rep.prioridad, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorPrioridad)
            }
            Text("${rep.id} · $fecha · ${rep.dispositivo} · ${rep.categoria}", fontSize = 11.sp, color = Paleta.Gris)
            Text(rep.descripcion, fontSize = 13.sp, color = Paleta.Texto, maxLines = if (expandida) Int.MAX_VALUE else 2)
            Text("Estado: ${rep.estado} · Atiende: ${rep.asignadoA ?: "sin asignar"}", fontSize = 11.sp, color = colorEstado, fontWeight = FontWeight.SemiBold)

            if (expandida) {
                HorizontalDivider()
                Text("Cambiar estado", fontSize = 11.sp, color = Paleta.Gris)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ESTADOS.forEach { e -> FilterChip(selected = e == rep.estado, onClick = { onEstado(e) }, label = { Text(e, fontSize = 12.sp) }) }
                }
                if (esAdmin) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = asignado, onValueChange = { asignado = it.take(60) }, singleLine = true,
                            label = { Text("Asignar a (correo del agente)", fontSize = 11.sp) }, modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { onAsignar(asignado) }, colors = ButtonDefaults.buttonColors(containerColor = Paleta.Oscuro)) { Text("Asignar") }
                    }
                }
                rep.notas.forEach { Text("📝 ${it.autor}: ${it.texto}", fontSize = 12.sp, color = Paleta.Verde) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = nota, onValueChange = { nota = it.take(300) }, singleLine = true,
                        label = { Text("Nota para el usuario", fontSize = 11.sp) }, modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = nota.isNotBlank(),
                        onClick = { onNota(nota); nota = "" },
                        colors = ButtonDefaults.buttonColors(containerColor = Paleta.Naranja)
                    ) { Text("Agregar") }
                }
                if (esAdmin) {
                    TextButton(onClick = onEliminar) { Text("🗑 Eliminar reporte", color = Color(0xFFB3391B)) }
                }
            }
        }
    }
}
