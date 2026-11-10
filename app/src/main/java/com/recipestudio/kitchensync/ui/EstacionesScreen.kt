package com.recipestudio.kitchensync.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recipestudio.kitchensync.network.ApiException
import com.recipestudio.kitchensync.network.ChatSnapshot
import com.recipestudio.kitchensync.network.KitchenApi
import com.recipestudio.kitchensync.network.ServerConfig
import com.recipestudio.kitchensync.security.InputValidator
import com.recipestudio.kitchensync.security.Validacion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Chat de estaciones (Prácticas 6 y 7): mensaje + emisor + hora, contador por estación,
 * rechazo de mensajes vacíos y aviso al enviar. El historial lo limpia el administrador desde la web.
 */
@Composable
fun EstacionesScreen() {
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
        SinVinculo("Vincula tu celular con el QR en la pestaña Receta para usar el chat.")
        return
    }
    val api = remember(activa) { KitchenApi(ServerConfig.leer(context), activa) }
    val miNombre = "📱 ${activa.dispositivo}"
    var chat by remember { mutableStateOf(ChatSnapshot(emptyList(), emptyMap())) }
    var texto by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val lista = rememberLazyListState()

    suspend fun refrescar() {
        try {
            chat = api.leerChat()
            error = null
        } catch (e: ApiException) {
            error = e.message
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refrescar()
            delay(2000L)
        }
    }
    LaunchedEffect(chat.mensajes.size) {
        if (chat.mensajes.isNotEmpty()) lista.animateScrollToItem(chat.mensajes.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(Paleta.Crema).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Chat de estaciones", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Paleta.Texto)

        // Contador de mensajes enviados por cada estación
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (chat.contadores.isEmpty()) Text("Aún no hay mensajes", color = Paleta.Gris, fontSize = 12.sp)
            chat.contadores.forEach { (quien, n) ->
                AssistChip(onClick = {}, label = { Text("$quien: $n", fontSize = 11.sp) })
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = lista, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chat.mensajes) { m ->
                val propio = m.emisor == miNombre
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (propio) Arrangement.End else Arrangement.Start) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (propio) Paleta.Tarjeta else Paleta.Oscuro),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            val fg = if (propio) Paleta.Texto else androidx.compose.ui.graphics.Color.White
                            Text("${m.emisor} · ${m.hora}", fontSize = 11.sp, color = if (propio) Paleta.Gris else androidx.compose.ui.graphics.Color.LightGray)
                            Text(m.texto, color = fg)
                        }
                    }
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it.take(InputValidator.MAX_MENSAJE) },
            label = { Text("Mensaje a otra estación...") },
            supportingText = { Text("${texto.length}/${InputValidator.MAX_MENSAJE}") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Paleta.Naranja),
            onClick = {
                val v = InputValidator.validarMensaje(texto)
                if (v is Validacion.Error) {
                    error = v.mensaje
                    return@Button
                }
                error = null
                val envio = texto
                scope.launch {
                    try {
                        chat = api.enviarChat(envio)
                        texto = ""
                        Toast.makeText(context, "Mensaje enviado", Toast.LENGTH_SHORT).show()
                    } catch (e: ApiException) {
                        error = e.message // el texto se conserva para reintentar
                    }
                }
            }
        ) { Text("Enviar alerta de cocina", fontWeight = FontWeight.Bold) }
    }
}

@Composable
internal fun SinVinculo(mensaje: String) {
    Box(Modifier.fillMaxSize().background(Paleta.Crema).padding(32.dp), contentAlignment = Alignment.Center) {
        Text(mensaje, color = Paleta.Gris, fontSize = 15.sp)
    }
}
