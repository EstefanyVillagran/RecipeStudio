package com.recipestudio.kitchensync.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.recipestudio.kitchensync.data.remote.FirebaseMuro
import com.recipestudio.kitchensync.data.remote.FirebaseReportes
import com.recipestudio.kitchensync.data.remote.FotoMuro
import com.recipestudio.kitchensync.network.ServerConfig
import com.recipestudio.kitchensync.shared.network.S3Exception
import com.recipestudio.kitchensync.shared.network.S3UploadClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Muro de recetas: las fotos de los platillos terminados. La foto completa se baja de S3 con una URL temporal. */
@Composable
fun MuroScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var fotos by remember { mutableStateOf<List<FotoMuro>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var miUid by remember { mutableStateOf<String?>(null) }
    var abierta by remember { mutableStateOf<FotoMuro?>(null) }

    LaunchedEffect(Unit) {
        miUid = runCatching { FirebaseReportes.asegurarSesion() }.getOrNull()
        FirebaseMuro.fotos()
            .catch { e -> error = e.message; cargando = false }
            .collect { fotos = it; error = null; cargando = false }
    }

    Column(Modifier.fillMaxSize().background(Paleta.Crema)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
            Text("Muro de recetas", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Paleta.Texto)
            Text("Lo que ya cocinaron. Termina una receta y sube tu foto final para aparecer aquí.", fontSize = 12.sp, color = Paleta.Gris)
        }
        error?.let {
            Card(Modifier.padding(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFBE0D9)), shape = RoundedCornerShape(14.dp)) {
                Text("No se pudo cargar el muro: $it", fontSize = 12.sp, color = Paleta.Texto, modifier = Modifier.padding(12.dp))
            }
        }
        when {
            cargando && error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Paleta.Naranja) }
            fotos.isEmpty() && error == null -> Column(
                Modifier.fillMaxSize().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🍽", fontSize = 48.sp)
                Text("Aún no hay fotos en el muro", fontWeight = FontWeight.Bold, color = Paleta.Texto)
                Text("Sé el primero: termina una receta y toma la foto final.", fontSize = 13.sp, color = Paleta.Gris)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(fotos, key = { it.id }) { f -> TarjetaFoto(f) { abierta = f } }
            }
        }
    }

    abierta?.let { f ->
        DetalleFoto(
            foto = f, esMia = f.ownerUid == miUid, onCerrar = { abierta = null },
            onEliminar = {
                abierta = null
                scope.launch {
                    val ok = FirebaseMuro.eliminar(f.id)
                    Toast.makeText(context, if (ok) "Foto eliminada del muro" else "No se pudo eliminar", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun TarjetaFoto(f: FotoMuro, onClick: () -> Unit) {
    val miniatura = remember(f.id, f.thumbB64) { bitmapDeBase64(f.thumbB64) }
    Card(
        Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column {
            if (miniatura != null) {
                Image(miniatura.asImageBitmap(), f.receta, Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFFEFE8DC)), contentAlignment = Alignment.Center) { Text("🍽", fontSize = 32.sp) }
            }
            Column(Modifier.padding(10.dp)) {
                Text(f.receta, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Paleta.Texto, maxLines = 1)
                Text("${f.autor} · ${haceTiempo(f.creadoEn)}", fontSize = 11.sp, color = Paleta.Gris, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DetalleFoto(foto: FotoMuro, esMia: Boolean, onCerrar: () -> Unit, onEliminar: () -> Unit) {
    val context = LocalContext.current
    val miniatura = remember(foto.id) { bitmapDeBase64(foto.thumbB64) }
    var completa by remember(foto.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var estado by remember(foto.id) { mutableStateOf(if (foto.key.isBlank()) "" else "Cargando foto completa…") }

    // Foto completa desde S3 con URL prefirmada (necesita la sesión del QR para pedirle la URL al servidor)
    LaunchedEffect(foto.id) {
        if (foto.key.isBlank()) return@LaunchedEffect
        val sesion = ServerConfig.sesion(context)
        if (sesion == null) { estado = "Vincula tu celular con el QR para ver la foto en grande"; return@LaunchedEffect }
        try {
            val url = S3UploadClient(ServerConfig.leer(context), sesion.sessionId, sesion.token).urlDescarga(foto.key)
            completa = withContext(Dispatchers.IO) { bitmapDeUrl(url) }
            estado = if (completa == null) "No se pudo cargar la foto completa" else ""
        } catch (e: S3Exception) {
            estado = if (e.noConfigurado) "" else (e.message ?: "No se pudo cargar la foto completa")
        }
    }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(foto.receta) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val b = completa ?: miniatura
                if (b != null) Image(b.asImageBitmap(), foto.receta, Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit)
                if (estado.isNotBlank()) Text(estado, fontSize = 12.sp, color = Paleta.Gris)
                Text("Por ${foto.autor} · ${haceTiempo(foto.creadoEn)}", fontSize = 12.sp, color = Paleta.Gris)
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
        dismissButton = if (esMia) ({ TextButton(onClick = onEliminar) { Text("Eliminar mi foto", color = Color(0xFFB3391B)) } }) else null
    )
}
