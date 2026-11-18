package com.recipestudio.kitchensync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.recipestudio.kitchensync.data.remote.RecetaPropia
import kotlinx.coroutines.launch

private val DIFICULTADES = listOf("Dificultad baja", "Dificultad media", "Dificultad alta")

/** Crear o editar una receta propia. Se guarda en la nube del usuario y se puede cocinar y sincronizar como cualquier otra. */
@Composable
fun CrearRecetaDialog(inicial: RecetaPropia?, onCerrar: () -> Unit, onGuardar: suspend (RecetaPropia) -> Boolean) {
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(inicial?.nombre.orEmpty()) }
    var categoria by remember { mutableStateOf(inicial?.categoria.orEmpty()) }
    var tiempo by remember { mutableStateOf(inicial?.tiempo.orEmpty()) }
    var porciones by remember { mutableStateOf(inicial?.porciones.orEmpty()) }
    var dificultad by remember { mutableStateOf(inicial?.dificultad?.takeIf { it in DIFICULTADES } ?: DIFICULTADES[1]) }
    var tip by remember { mutableStateOf(inicial?.chefTip.orEmpty()) }
    val ings = remember { mutableStateListOf<Pair<String, String>>().apply { addAll(inicial?.ingredientes?.ifEmpty { null } ?: listOf("" to "", "" to "")) } }
    val pasos = remember { mutableStateListOf<String>().apply { addAll(inicial?.pasos?.ifEmpty { null } ?: listOf("")) } }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!guardando) onCerrar() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Paleta.Crema) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (inicial == null) "Nueva receta" else "Editar receta", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Paleta.Texto)
                    TextButton(onClick = onCerrar, enabled = !guardando) { Text("Cancelar", color = Paleta.Gris) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = nombre, onValueChange = { nombre = it.take(60) }, label = { Text("Nombre de la receta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = categoria, onValueChange = { categoria = it.take(40) }, label = { Text("Categoría (ej. Postres, Cena)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = tiempo, onValueChange = { tiempo = it.take(20) }, label = { Text("Tiempo") }, placeholder = { Text("30 min") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = porciones, onValueChange = { porciones = it.take(20) }, label = { Text("Porciones") }, placeholder = { Text("4 porciones") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DIFICULTADES.forEach { d -> FilterChip(selected = d == dificultad, onClick = { dificultad = d }, label = { Text(d.removePrefix("Dificultad ").replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }) }
                    }

                    Text("Ingredientes", fontWeight = FontWeight.Bold, color = Paleta.Texto)
                    ings.forEachIndexed { i, par ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = par.first, onValueChange = { ings[i] = ings[i].copy(first = it.take(80)) }, label = { Text("Ingrediente") }, singleLine = true, modifier = Modifier.weight(2f))
                            OutlinedTextField(value = par.second, onValueChange = { ings[i] = ings[i].copy(second = it.take(30)) }, label = { Text("Cantidad") }, singleLine = true, modifier = Modifier.weight(1.2f))
                            TextButton(onClick = { if (ings.size > 1) ings.removeAt(i) }, enabled = ings.size > 1) { Text("✖") }
                        }
                    }
                    if (ings.size < 40) OutlinedButton(onClick = { ings.add("" to "") }) { Text("➕ Agregar ingrediente") }

                    Text("Pasos", fontWeight = FontWeight.Bold, color = Paleta.Texto)
                    pasos.forEachIndexed { i, p ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = p, onValueChange = { pasos[i] = it.take(300) }, label = { Text("Paso ${i + 1}") }, minLines = 2, modifier = Modifier.weight(1f))
                            TextButton(onClick = { if (pasos.size > 1) pasos.removeAt(i) }, enabled = pasos.size > 1) { Text("✖") }
                        }
                    }
                    if (pasos.size < 15) OutlinedButton(onClick = { pasos.add("") }) { Text("➕ Agregar paso") }

                    OutlinedTextField(value = tip, onValueChange = { tip = it.take(300) }, label = { Text("Consejo del chef (opcional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    Button(
                        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), enabled = !guardando,
                        colors = ButtonDefaults.buttonColors(containerColor = Paleta.Naranja),
                        onClick = {
                            val ingsOk = ings.filter { it.first.isNotBlank() }
                            val pasosOk = pasos.filter { it.isNotBlank() }
                            error = when {
                                nombre.trim().length < 3 -> "Ponle un nombre de al menos 3 letras"
                                ingsOk.isEmpty() -> "Agrega al menos un ingrediente"
                                ingsOk.map { it.first.trim().lowercase() }.toSet().size != ingsOk.size -> "Hay ingredientes repetidos: cambia el nombre de uno"
                                pasosOk.isEmpty() -> "Agrega al menos un paso"
                                else -> null
                            }
                            if (error != null) return@Button
                            guardando = true
                            scope.launch {
                                val ok = onGuardar(RecetaPropia(
                                    id = inicial?.id.orEmpty(), nombre = nombre.trim(), categoria = categoria.trim(), tiempo = tiempo.trim(),
                                    dificultad = dificultad, porciones = porciones.trim(), chefTip = tip.trim(), pasos = pasosOk, ingredientes = ingsOk,
                                    creadoEn = inicial?.creadoEn ?: 0L
                                ))
                                guardando = false
                                if (ok) onCerrar() else error = "No se pudo guardar. Revisa tu internet e intenta de nuevo"
                            }
                        }
                    ) {
                        if (guardando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.White)
                        else Text("Guardar receta", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
