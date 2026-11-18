package com.recipestudio.kitchensync.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.recipestudio.kitchensync.domain.EstimadorSabor
import com.recipestudio.kitchensync.domain.NivelImpacto
import com.recipestudio.kitchensync.domain.SubstitutionCalculator
import com.recipestudio.kitchensync.domain.SustitucionContexto
import com.recipestudio.kitchensync.network.AnalisisIa
import com.recipestudio.kitchensync.network.ApiException
import com.recipestudio.kitchensync.network.GeminiSustitucion
import com.recipestudio.kitchensync.network.IaException
import com.recipestudio.kitchensync.network.KitchenApi
import com.recipestudio.kitchensync.network.ServerConfig
import kotlinx.coroutines.launch
import java.io.File

/** "No tengo este ingrediente": foto de lo que sí tienes y una IA estima cuánto cambia el sabor y la textura. */
@Composable
fun SustitucionFotoPanel(onVerSugerencias: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var faltante by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("") }
    var foto by remember { mutableStateOf<Bitmap?>(null) }
    var cargando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<AnalisisIa?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var uriCamara by remember { mutableStateOf<Uri?>(null) }
    var tengo by remember { mutableStateOf("") }
    var sinCatalogo by remember { mutableStateOf(false) }

    fun nuevaFoto(b: Bitmap?) {
        if (b == null) { error = "No se pudo abrir esa imagen"; return }
        foto = b; resultado = null; error = null
    }

    // Cámara en resolución completa (la miniatura de TakePicturePreview es muy chica para que la IA reconozca bien)
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = uriCamara
        if (ok && u != null) nuevaFoto(leerReducida(context, u, 1024))
    }
    fun lanzarCamara() {
        val dir = File(context.cacheDir, "fotos").apply { mkdirs() }
        val archivo = File(dir, "sust_${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
        uriCamara = u
        camara.launch(u)
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) lanzarCamara() else error = "Necesito permiso de cámara para tomar la foto (o elige una de la galería)"
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) nuevaFoto(leerReducida(context, uri, 1024))
    }

    Column(
        Modifier.fillMaxSize().background(Paleta.Crema).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Sustituir con foto", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Paleta.Texto)
        Text("Dinos qué te falta, tómale foto a lo que sí tienes y una IA te dice cuánto cambiaría el sabor.", color = Paleta.Gris, fontSize = 13.sp)

        if (!GeminiSustitucion.configurado) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3D6)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("La IA no está activada en esta versión", fontWeight = FontWeight.Bold, color = Paleta.Texto)
                    Text("Falta la clave GEMINI_API_KEY al compilar. Mientras tanto usa el cálculo sin IA que está más abajo.", fontSize = 12.sp, color = Paleta.Texto)
                    OutlinedButton(onClick = onVerSugerencias) { Text("Ver sugerencias") }
                }
            }
        }

        // ---- Paso 1 ----
        PasoCard(1, "¿Qué ingrediente te falta?") {
            val sugeridos = SustitucionContexto.ingredientes
            if (sugeridos.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sugeridos.forEach { n -> FilterChip(selected = n == faltante, onClick = { faltante = n }, label = { Text(n, fontSize = 12.sp) }) }
                }
            }
            OutlinedTextField(
                value = faltante, onValueChange = { faltante = it.take(60) }, singleLine = true,
                label = { Text("Ingrediente que te falta") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = cantidad, onValueChange = { cantidad = it.take(30) }, singleLine = true,
                label = { Text("Cantidad que pide la receta (opcional)") }, modifier = Modifier.fillMaxWidth()
            )
        }

        // ---- Paso 2 ----
        PasoCard(2, "Foto de lo que sí tienes") {
            val f = foto
            if (f != null) {
                Image(
                    bitmap = f.asImageBitmap(), contentDescription = "Producto que tienes",
                    modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFEFE8DC)),
                    contentAlignment = Alignment.Center
                ) { Text("📷\nAquí aparecerá tu foto", fontSize = 13.sp, color = Paleta.Gris) }
            }
            Text("Tip: buena luz, el producto centrado y la etiqueta a la vista.", fontSize = 11.sp, color = Paleta.Gris)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val concedido = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (concedido) lanzarCamara() else permiso.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Paleta.Naranja)
                ) { Text(if (f == null) "📷 Tomar foto" else "📷 Repetir") }
                OutlinedButton(onClick = { galeria.launch("image/*") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("🖼 Galería") }
            }
        }

        // ---- Sin IA: cálculo local (no necesita internet, clave ni saldo) ----
        PasoCard(3, "¿Sin IA o sin internet? Escribe qué tienes") {
            OutlinedTextField(
                value = tengo, onValueChange = { tengo = it.take(60); sinCatalogo = false }, singleLine = true,
                label = { Text("Producto que tienes (ej. Queso Oaxaca)") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                enabled = faltante.isNotBlank() && tengo.isNotBlank(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                onClick = {
                    val e = EstimadorSabor.estimar(faltante, tengo)
                    sinCatalogo = e == null
                    error = null
                    resultado = e?.let {
                        AnalisisIa(
                            producto = it.tengo.replaceFirstChar { c -> c.uppercase() }, sirve = it.sirve,
                            impactoSaborPct = it.saborPct, impactoTexturaPct = it.texturaPct, cantidadSugerida = "",
                            nota = "", confianza = "local", consejo = it.consejo, alternativas = emptyList()
                        )
                    }
                }
            ) { Text("🧮 Calcular sin IA") }
            if (sinCatalogo) Text("No conozco alguno de esos ingredientes. Prueba con otro nombre (ej. \"azúcar\", \"queso Oaxaca\", \"aceite de oliva\").", fontSize = 12.sp, color = Paleta.Gris)
        }

        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFBE0D9)), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(it, fontSize = 13.sp, color = Paleta.Texto)
                    val respaldo = if (faltante.isNotBlank()) SubstitutionCalculator.analizar(faltante, 1.0) else null
                    if (respaldo != null) {
                        Text("Mientras tanto, según las reglas guardadas: ${respaldo.original} → ${respaldo.sustituto} " +
                            "(sabor ${respaldo.impactoSaborPct}%, textura ${respaldo.impactoTexturaPct}%).", fontSize = 12.sp, color = Paleta.Verde)
                    }
                    TextButton(onClick = onVerSugerencias) { Text("Ver sugerencias guardadas") }
                }
            }
        }

        // ---- Paso 3 ----
        Button(
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
            enabled = foto != null && faltante.isNotBlank() && !cargando && GeminiSustitucion.configurado,
            colors = ButtonDefaults.buttonColors(containerColor = Paleta.Oscuro),
            onClick = {
                val f = foto ?: return@Button
                cargando = true; error = null; resultado = null
                scope.launch {
                    try {
                        resultado = GeminiSustitucion.analizar(faltante.trim(), cantidad.trim(), aJpeg(f, 1024, 85), SustitucionContexto.receta)
                    } catch (e: IaException) {
                        error = e.message
                    } finally {
                        cargando = false
                    }
                }
            }
        ) {
            if (cargando) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Paleta.Crema)
                Spacer(Modifier.width(10.dp))
                Text("Analizando…", fontWeight = FontWeight.Bold)
            } else Text("✨ Analizar con IA", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        resultado?.let { r ->
            ResultadoIa(faltante.trim(), r,
                onUsar = {
                    val sesion = ServerConfig.sesion(context)
                    if (sesion == null) {
                        Toast.makeText(context, "Vincula tu celular con el QR para enviarlo a la estación", Toast.LENGTH_LONG).show()
                    } else scope.launch {
                        try {
                            KitchenApi(ServerConfig.leer(context), sesion).enviarSustitucion(faltante.trim(), r.producto, r.impactoSaborPct, r.impactoTexturaPct, r.nota)
                            Toast.makeText(context, "✅ Enviado a la pantalla de la estación", Toast.LENGTH_SHORT).show()
                        } catch (e: ApiException) {
                            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onOtraFoto = { resultado = null; foto = null },
                onAlternativa = { alt -> Toast.makeText(context, "Prueba tomando foto de: $alt", Toast.LENGTH_SHORT).show() }
            )
        }
    }
}

@Composable
private fun PasoCard(numero: Int, titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Paleta.Naranja), contentAlignment = Alignment.Center) {
                    Text(numero.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text(titulo, fontWeight = FontWeight.Bold, color = Paleta.Texto)
            }
            contenido()
        }
    }
}

@Composable
private fun ResultadoIa(faltante: String, r: AnalisisIa, onUsar: () -> Unit, onOtraFoto: () -> Unit, onAlternativa: (String) -> Unit) {
    val nivel = SubstitutionCalculator.nivelDe(r.impactoSaborPct, r.impactoTexturaPct)
    val color = if (!r.sirve) Color(0xFFB3391B) else when (nivel) {
        NivelImpacto.BAJO -> Paleta.Verde
        NivelImpacto.MODERADO -> Paleta.Amarillo
        NivelImpacto.ALTO -> Paleta.Naranja
    }
    val veredicto = when {
        !r.sirve -> "No es un buen sustituto"
        nivel == NivelImpacto.BAJO -> "Sirve casi igual"
        nivel == NivelImpacto.MODERADO -> "Sirve, con cambio moderado"
        else -> "Sirve, pero cambia bastante"
    }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().background(color).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(veredicto, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${r.producto} en lugar de $faltante", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Medidor("Cambio de sabor", r.impactoSaborPct, color)
                Medidor("Cambio de textura", r.impactoTexturaPct, color)
                if (r.cantidadSugerida.isNotBlank()) Text("📏 Cantidad sugerida: ${r.cantidadSugerida}", color = Paleta.Texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (r.consejo.isNotBlank()) Text("💡 ${r.consejo}", color = Paleta.Texto, fontSize = 13.sp)
                if (r.nota.isNotBlank()) Text(r.nota, color = Paleta.Gris, fontSize = 12.sp)
                if (r.alternativas.isNotEmpty()) {
                    Text("Otras opciones", fontSize = 11.sp, color = Paleta.Gris)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.alternativas.forEach { a -> AssistChip(onClick = { onAlternativa(a) }, label = { Text(a, fontSize = 12.sp) }) }
                    }
                }
                Text(
                    if (r.confianza == "local") "Cálculo sin IA con perfiles de sabor del catálogo: es una aproximación, no una medición."
                    else "Confianza de la IA: ${r.confianza}${if (r.confianza == "baja") " — prueba con una foto más clara" else ""}. Es una estimación, no una medición exacta.",
                    color = Paleta.Gris, fontSize = 11.sp
                )
                Button(
                    onClick = onUsar, enabled = r.sirve, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Paleta.Verde)
                ) { Text("Usar este sustituto y avisar a la estación", fontWeight = FontWeight.Bold) }
                TextButton(onClick = onOtraFoto, modifier = Modifier.fillMaxWidth()) { Text("Probar con otro producto", color = Paleta.Gris) }
            }
        }
    }
}

@Composable
private fun Medidor(titulo: String, pct: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(titulo, fontSize = 13.sp, color = Paleta.Texto)
            Text("$pct%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
        LinearProgressIndicator(
            progress = { pct / 100f }, color = color, trackColor = Color(0xFFEFE8DC),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
    }
}
