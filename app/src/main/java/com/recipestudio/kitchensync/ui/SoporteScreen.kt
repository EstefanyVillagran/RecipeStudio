package com.recipestudio.kitchensync.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recipestudio.kitchensync.data.remote.FirebaseException
import com.recipestudio.kitchensync.data.remote.FirebaseReportes
import com.recipestudio.kitchensync.data.remote.ReporteFs
import com.recipestudio.kitchensync.network.ServerConfig
import kotlinx.coroutines.flow.catch
import com.recipestudio.kitchensync.security.InputValidator
import com.recipestudio.kitchensync.security.Validacion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CATEGORIAS = listOf("Sincronización", "Receta", "QR / Vinculación", "Biometría", "Otro")
private val PRIORIDADES = listOf("Baja", "Media", "Alta")

/**
 * Soporte (Práctica 7). El celular reporta problemas y ve el estado de los suyos.
 * Los reportes viven en Firebase Firestore; el administrador y los agentes los atienden desde admin.html.
 */
@Composable
fun SoporteScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sesion by remember { mutableStateOf(ServerConfig.sesion(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            sesion = ServerConfig.sesion(context)
            delay(2000L)
        }
    }
    val activa = sesion
    if (activa == null) {
        SinVinculo("Vincula tu celular con el QR en la pestaña Receta para reportar problemas.")
        return
    }
    var titulo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf(CATEGORIAS.last()) }
    var prioridad by remember { mutableStateOf("Media") }
    var error by remember { mutableStateOf<String?>(null) }
    var misReportes by remember { mutableStateOf<List<ReporteFs>>(emptyList()) }
    var enviando by remember { mutableStateOf(false) }
    var errorLista by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(true) }

    // Tiempo real con Firestore: la lista se actualiza sola cuando el admin cambia el estado.
    LaunchedEffect(Unit) {
        try {
            val uid = FirebaseReportes.asegurarSesion()
            FirebaseReportes.misReportes(uid)
                .catch { e -> errorLista = e.message; cargando = false }
                .collect { misReportes = it; errorLista = null; cargando = false }
        } catch (e: FirebaseException) {
            errorLista = e.message
            cargando = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(Paleta.Crema).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Soporte", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Paleta.Texto)
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Paleta.Tarjeta)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reportar un problema", fontWeight = FontWeight.Bold, color = Paleta.Texto)
                OutlinedTextField(
                    value = titulo, onValueChange = { titulo = it.take(InputValidator.MAX_TITULO) },
                    label = { Text("Título (ej. Receta no disponible)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = descripcion, onValueChange = { descripcion = it.take(InputValidator.MAX_DESCRIPCION) },
                    label = { Text("Descripción") }, minLines = 2, modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CATEGORIAS.forEach { c -> FilterChip(selected = c == categoria, onClick = { categoria = c }, label = { Text(c, fontSize = 12.sp) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRIORIDADES.forEach { p -> FilterChip(selected = p == prioridad, onClick = { prioridad = p }, label = { Text(p, fontSize = 12.sp) }) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !enviando,
                    colors = ButtonDefaults.buttonColors(containerColor = Paleta.Naranja),
                    onClick = {
                        val v = InputValidator.validarReporte(titulo, descripcion)
                        if (v is Validacion.Error) {
                            error = v.mensaje
                            return@Button
                        }
                        error = null
                        enviando = true
                        scope.launch {
                            try {
                                val id = FirebaseReportes.crear(titulo, descripcion, categoria, prioridad, activa.dispositivo)
                                titulo = ""
                                descripcion = ""
                                Toast.makeText(context, "Reporte $id enviado", Toast.LENGTH_SHORT).show()
                            } catch (e: FirebaseException) {
                                error = e.message
                            } finally {
                                enviando = false
                            }
                        }
                    }
                ) { Text("Enviar reporte", fontWeight = FontWeight.Bold) }
            }
        }

        Text("Mis reportes (${misReportes.size})", fontWeight = FontWeight.Bold, color = Paleta.Texto)
        errorLista?.let {
            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFBE0D9))) {
                Text("No se pudieron cargar tus reportes: $it", color = Paleta.Texto, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
        }
        if (cargando && errorLista == null) Text("Cargando…", color = Paleta.Gris, fontSize = 13.sp)
        else if (misReportes.isEmpty() && errorLista == null) Text("No has enviado reportes todavía.", color = Paleta.Gris, fontSize = 13.sp)
        misReportes.forEach { rep -> CasoCard(rep) }
    }
}

@Composable
private fun CasoCard(rep: ReporteFs) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${rep.id} · ${rep.titulo}", fontWeight = FontWeight.Bold, color = Paleta.Texto, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(rep.estado, fontSize = 11.sp) })
            }
            Text("${rep.categoria} · Prioridad ${rep.prioridad}", fontSize = 11.sp, color = Paleta.Gris)
            Text(rep.descripcion, color = Paleta.Texto, fontSize = 13.sp)
            Text("Atiende: ${rep.asignadoA ?: "sin asignar"}", fontSize = 11.sp, color = Paleta.Gris)
            rep.notas.forEach { Text("📝 ${it.autor}: ${it.texto}", fontSize = 12.sp, color = Paleta.Verde) }
        }
    }
}
