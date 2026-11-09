package com.recipestudio.kitchensync.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recipestudio.kitchensync.domain.NivelImpacto
import com.recipestudio.kitchensync.domain.SubstitutionCalculator
import com.recipestudio.kitchensync.domain.SustitucionContexto
import com.recipestudio.kitchensync.network.AnalisisIa
import com.recipestudio.kitchensync.network.GeminiSustitucion
import com.recipestudio.kitchensync.network.IaException
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/** "No tengo este ingrediente": foto de lo que sí tienes y una IA estima cuánto cambia el sabor. */
@Composable
fun SustitucionFotoPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var faltante by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("") }
    var foto by remember { mutableStateOf<Bitmap?>(null) }
    var cargando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<AnalisisIa?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) { foto = bmp; resultado = null; error = null }
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bmp = leerReducida(context, uri)
            if (bmp != null) { foto = bmp; resultado = null; error = null } else error = "No se pudo abrir esa imagen"
        }
    }

    Column(
        Modifier.fillMaxSize().background(Paleta.Crema).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("¿Con qué lo vas a sustituir?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Paleta.Texto)
        Text("Dinos qué ingrediente te falta y tómale foto a lo que sí tienes. La IA estima cuánto cambiaría el sabor.", color = Paleta.Gris, fontSize = 13.sp)

        val sugeridos = SustitucionContexto.ingredientes
        if (sugeridos.isNotEmpty()) {
            Text("Ingredientes de tu receta", fontSize = 11.sp, color = Paleta.Gris)
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { camara.launch(null) }, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Paleta.Naranja)
            ) { Text("📷 Tomar foto") }
            OutlinedButton(onClick = { galeria.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("🖼 Galería") }
        }

        foto?.let {
            Image(
                bitmap = it.asImageBitmap(), contentDescription = "Producto que tienes",
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = foto != null && faltante.isNotBlank() && !cargando,
            colors = ButtonDefaults.buttonColors(containerColor = Paleta.Oscuro),
            onClick = {
                val f = foto ?: return@Button
                cargando = true
                error = null
                resultado = null
                scope.launch {
                    try {
                        resultado = GeminiSustitucion.analizar(faltante.trim(), cantidad.trim(), aJpeg(f))
                    } catch (e: IaException) {
                        error = e.message
                    } finally {
                        cargando = false
                    }
                }
            }
        ) {
            if (cargando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Paleta.Crema)
            else Text("Analizar con IA", fontWeight = FontWeight.Bold)
        }

        resultado?.let { r -> ResultadoIa(faltante.trim(), r) }
    }
}

@Composable
private fun ResultadoIa(faltante: String, r: AnalisisIa) {
    val color = when (SubstitutionCalculator.nivelDe(r.impactoSaborPct, r.impactoTexturaPct)) {
        NivelImpacto.BAJO -> Paleta.Verde
        NivelImpacto.MODERADO -> Paleta.Amarillo
        NivelImpacto.ALTO -> Paleta.Naranja
    }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Paleta.Tarjeta)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Detecté: ${r.producto}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Paleta.Texto)
            Text(
                if (r.sirve) "✅ Sirve en lugar de $faltante" else "⚠️ No es un buen sustituto de $faltante",
                color = if (r.sirve) Paleta.Verde else Paleta.Naranja, fontWeight = FontWeight.SemiBold
            )
            if (r.cantidadSugerida.isNotBlank()) Text("Cantidad sugerida: ${r.cantidadSugerida}", color = Paleta.Texto)
            Text("Cambio de sabor: ${r.impactoSaborPct}%", color = Paleta.Texto)
            LinearProgressIndicator(progress = { r.impactoSaborPct / 100f }, color = color, modifier = Modifier.fillMaxWidth())
            Text("Cambio de textura: ${r.impactoTexturaPct}%", color = Paleta.Texto)
            LinearProgressIndicator(progress = { r.impactoTexturaPct / 100f }, color = color, modifier = Modifier.fillMaxWidth())
            if (r.nota.isNotBlank()) Text(r.nota, color = Paleta.Gris, fontSize = 13.sp)
            Text("Es una estimación de una IA, no una medición exacta.", color = Paleta.Gris, fontSize = 11.sp)
        }
    }
}

private fun aJpeg(b: Bitmap): ByteArray {
    val r = reducir(b)
    return ByteArrayOutputStream().also { r.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
}

private fun reducir(b: Bitmap, max: Int = 768): Bitmap {
    val lado = maxOf(b.width, b.height)
    if (lado <= max) return b
    val f = max.toFloat() / lado
    return Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true)
}

/** Lee una imagen de la galería sin cargarla completa en memoria. */
private fun leerReducida(context: Context, uri: Uri): Bitmap? = try {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
    var muestreo = 1
    while (maxOf(limites.outWidth, limites.outHeight) / muestreo > 1536) muestreo *= 2
    val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }?.let { reducir(it) }
} catch (e: Exception) {
    null
}
