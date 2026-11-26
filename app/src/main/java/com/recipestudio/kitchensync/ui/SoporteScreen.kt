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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recipestudio.kitchensync.data.remote.FirebaseException
import com.recipestudio.kitchensync.data.remote.FirebaseReportes
import com.recipestudio.kitchensync.data.remote.ReporteFs
import com.recipestudio.kitchensync.network.ServerConfig
import com.recipestudio.kitchensync.security.InputValidator
import com.recipestudio.kitchensync.security.Validacion
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val CATEGORIAS = listOf(
    "Sincronización" to "🔄", "Receta" to "🍳", "QR / Vinculación" to "📷", "Biometría" to "🔐", "Otro" to "💬"
)
private val PRIORIDADES = listOf("Baja" to "🟢", "Media" to "🟡", "Alta" to "🔴")

/** Texto sencillo para el usuario (en Firebase el estado se llama Nuevo / En proceso / Resuelto). */
private fun estadoVisible(e: String) = when (e) { "Nuevo" -> "Recibido"; "En proceso" -> "En revisión"; else -> e }
private fun colorEstado(e: String) = when (e) { "Resuelto" -> Paleta.Verde; "En proceso" -> Paleta.Amarillo; else -> Paleta.Naranja }

/**
 * Ayuda y reportes. No depende del QR: los reportes viven en la cuenta anónima de la app (Firebase), así que
 * siguen ahí aunque el usuario salga de la sesión de la estación.
 */
@Composable
fun SoporteScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var titulo by rememberSaveable { mutableStateOf("") }
    var descripcion by rememberSaveable { mutableStateOf("") }
    var categoria by rememberSaveable { mutableStateOf("Otro") }
    var prioridad by rememberSaveable { mutableStateOf("Media") }
    var idPendiente by rememberSaveable { mutableStateOf<String?>(null) }   // mismo id al reintentar: no duplica
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    var reportes by remember { mutableStateOf<List<ReporteFs>>(emptyList()) }
    var errorLista by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val uid = FirebaseReportes.asegurarSesion()
            FirebaseReportes.misReportes(uid)
                .catch { e -> errorLista = e.message; cargando = false }
                .collect { reportes = it; errorLista = null; cargando = false }
        } catch (e: FirebaseException) {
            errorLista = e.message
            cargando = false
        }
    }

    Column(Modifier.fillMaxSize().background(Paleta.Crema)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text("Ayuda y reportes", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Paleta.Texto)
            Text("Tus reportes se guardan en la nube: siguen aquí aunque salgas de la sesión.", fontSize = 12.sp, color = Paleta.Gris)
        }
        TabRow(selectedTabIndex = tab, containerColor = Paleta.Crema, contentColor = Paleta.Naranja) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Nuevo reporte") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Mis reportes (${reportes.size})") })
        }

        if (tab == 0) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("¿Qué pasó?", fontWeight = FontWeight.Bold, color = Paleta.Texto)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CATEGORIAS.forEach { (c, emoji) ->
                        FilterChip(selected = c == categoria, onClick = { categoria = c }, label = { Text("$emoji $c", fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(
                    value = titulo, onValueChange = { titulo = it.take(InputValidator.MAX_TITULO) },
                    label = { Text("Título corto") }, placeholder = { Text("Ej. No se vincula el QR") }, singleLine = true,
                    supportingText = { Text("${titulo.length}/${InputValidator.MAX_TITULO}") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = descripcion, onValueChange = { descripcion = it.take(InputValidator.MAX_DESCRIPCION) },
                    label = { Text("Cuéntanos qué hiciste y qué viste") }, minLines = 3,
                    supportingText = { Text("${descripcion.length}/${InputValidator.MAX_DESCRIPCION}") }, modifier = Modifier.fillMaxWidth()
                )
                Text("¿Qué tan urgente es?", fontWeight = FontWeight.Bold, color = Paleta.Texto)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRIORIDADES.forEach { (p, emoji) ->
                        FilterChip(selected = p == prioridad, onClick = { prioridad = p }, label = { Text("$emoji $p") })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                Button(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !enviando,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Paleta.Naranja),
                    onClick = {
                        val v = InputValidator.validarReporte(titulo, descripcion)
                        if (v is Validacion.Error) { error = v.mensaje; return@Button }
                        error = null
                        enviando = true
                        val id = idPendiente ?: ("REP-" + System.currentTimeMillis()).also { idPendiente = it }
                        scope.launch {
                            try {
                                val enviado = withTimeoutOrNull(7000L) {
                                    FirebaseReportes.crear(titulo, descripcion, categoria, prioridad, ServerConfig.nombreDispositivo(), id)
                                }
                                Toast.makeText(
                                    context,
                                    if (enviado != null) "✅ Reporte enviado" else "📴 Sin conexión: tu reporte se enviará solo cuando vuelva internet",
                                    Toast.LENGTH_LONG
                                ).show()
                                titulo = ""; descripcion = ""; idPendiente = null; tab = 1
                            } catch (e: FirebaseException) {
                                error = e.message     // se conserva el id: reintentar no crea otro reporte
                            } finally {
                                enviando = false
                            }
                        }
                    }
                ) {
                    if (enviando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("Enviar reporte", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                errorLista?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFBE0D9)), shape = RoundedCornerShape(14.dp)) {
                        Text("No se pudieron cargar tus reportes: $it", color = Paleta.Texto, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                    }
                }
                when {
                    cargando && errorLista == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Paleta.Naranja)
                    }
                    reportes.isEmpty() && errorLista == null -> Column(
                        Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📭", fontSize = 44.sp)
                        Text("Aún no tienes reportes", fontWeight = FontWeight.Bold, color = Paleta.Texto)
                        Text("Si algo no funciona, cuéntanoslo y te respondemos aquí.", color = Paleta.Gris, fontSize = 13.sp)
                        OutlinedButton(onClick = { tab = 0 }) { Text("Crear un reporte") }
                    }
                    else -> reportes.forEach { CasoCard(it) }
                }
            }
        }
    }
}

@Composable
private fun CasoCard(rep: ReporteFs) {
    val color = colorEstado(rep.estado)
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(rep.titulo, fontWeight = FontWeight.Bold, color = Paleta.Texto, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(if (rep.pendiente) "⏳ Enviando…" else estadoVisible(rep.estado), color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text("${rep.categoria} · ${haceTiempo(rep.creadoEn)}", fontSize = 11.sp, color = Paleta.Gris)
                Text(rep.descripcion, color = Paleta.Texto, fontSize = 13.sp)
                if (rep.notas.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F2EA)), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Respuesta del soporte", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Paleta.Verde)
                            rep.notas.forEach { Text(it.texto, fontSize = 13.sp, color = Paleta.Texto) }
                        }
                    }
                }
            }
        }
    }
}
