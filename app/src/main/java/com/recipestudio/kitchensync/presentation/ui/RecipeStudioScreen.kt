package com.recipestudio.kitchensync.presentation.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.recipestudio.kitchensync.data.local.ProgresoLocal
import com.recipestudio.kitchensync.data.remote.FirebaseMuro
import com.recipestudio.kitchensync.util.AlertaTemporizador
import com.recipestudio.kitchensync.util.TrabajoSegundoPlano
import kotlinx.coroutines.CancellationException
import com.recipestudio.kitchensync.data.remote.FirebaseProgreso
import com.recipestudio.kitchensync.shared.network.S3Exception
import com.recipestudio.kitchensync.shared.network.S3UploadClient
import com.recipestudio.kitchensync.ui.aJpeg
import com.recipestudio.kitchensync.ui.leerReducida
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.flow.catch
import com.recipestudio.kitchensync.ui.CrearRecetaDialog
import com.recipestudio.kitchensync.domain.SustitucionContexto
import com.recipestudio.kitchensync.data.session.InsumoSubstitutionManager
import com.recipestudio.kitchensync.network.ServerConfig
import com.recipestudio.kitchensync.network.ServerDiscovery
import com.recipestudio.kitchensync.shared.hardware.BiometricAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.Executors

private val CreamBg = Color(0xFFF7F4EE)
private val DarkCard = Color(0xFF222428)
private val PrimaryOrange = Color(0xFFD65836)
private val LightOrangeCard = Color(0xFFFAF1E6)
private val TextDark = Color(0xFF1E1E1E)
private val SuccessGreen = Color(0xFF558B6E)
private val TimerYellow = Color(0xFFE5B04E)

data class IngredientUi(
    val id: String,
    val nombre: String,
    val cantidad: String,
    val completado: Boolean = false
)

data class RecetaUiData(
    val nombre: String,
    val categoria: String,
    val tiempo: String,
    val dificultad: String,
    val porciones: String,
    val pasoActual: Int,
    val totalPasos: Int,
    val instruccion: String,
    val chefTip: String,
    val ingredientes: List<IngredientUi>,
    val pasos: List<String> = emptyList(),   // pasos para navegar (Anterior / Siguiente)
    val propiaId: String? = null             // id en Firestore si es una receta creada por el usuario
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeStudioScreen(onSoporte: () -> Unit = {}, onIrSustitutos: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val sessionPrefs = context.getSharedPreferences("kitchen_session_state", Context.MODE_PRIVATE)
    var estaVinculado by remember {
        mutableStateOf(sessionPrefs.getBoolean("vinculado", false))
    }
    var tienePermisoCamara by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcherPermisos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { concedido -> tienePermisoCamara = concedido }
    )

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var recetaSecretaDesbloqueada by remember { mutableStateOf(false) }
    var sessionId by remember { mutableStateOf(sessionPrefs.getString("session_id", "demo-session") ?: "demo-session") }
    var sessionToken by remember { mutableStateOf(sessionPrefs.getString("session_token", "demo-token") ?: "demo-token") }
    var serverBaseUrl by remember {
        mutableStateOf(ServerConfig.normalizar(sessionPrefs.getString("server_base_url", ServerConfig.DEFAULT)))
    }
    var recetaFinalizada by remember { mutableStateOf(false) }
    var conectando by remember { mutableStateOf(false) }
    var menuSesion by remember { mutableStateOf(false) }
    var confirmarSalir by remember { mutableStateOf(false) }

    var ultimaFotoUri by remember { mutableStateOf<Uri?>(null) }
    var recetaHecha by remember { mutableStateOf(false) }

    var fotoNueva by remember { mutableStateOf<Bitmap?>(null) }
    var uriFotoFinal by remember { mutableStateOf<Uri?>(null) }
    // Foto en resolución completa: la miniatura de la cámara es muy chica para el muro y para S3
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = uriFotoFinal
        if (ok && u != null) fotoNueva = leerReducida(context, u, 1280)
    }
    fun lanzarFotoFinal() {
        val dir = java.io.File(context.cacheDir, "fotos").apply { mkdirs() }
        val u = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", java.io.File(dir, "final_${System.currentTimeMillis()}.jpg")
        )
        uriFotoFinal = u
        takePictureLauncher.launch(u)
    }

    val sustitucionAzucar = remember {
        InsumoSubstitutionManager.obtenerSustituto("Azúcar")
            ?: InsumoSubstitutionManager.obtenerSustituto("Azúcar Blanca")
    }

    val recetasBase = remember { recetasBase() }
    var recetasPropias by remember { mutableStateOf<List<RecetaUiData>>(emptyList()) }
    val recetasRemotas = remember { mutableStateListOf<RecetaUiData>() }   // las eligió otro celular y este no las tiene
    val recetasCat: List<RecetaUiData> = recetasBase + recetasPropias +
        recetasRemotas.filter { r -> (recetasBase + recetasPropias).none { it.nombre == r.nombre } }
    // El sondeo corre en una corrutina que nace una sola vez: necesita siempre el catálogo vigente, no el de su primera composición
    val catalogoActual by rememberUpdatedState(recetasCat)
    var editandoReceta by remember { mutableStateOf<com.recipestudio.kitchensync.data.remote.RecetaPropia?>(null) }
    var creandoReceta by remember { mutableStateOf(false) }
    var recetaABorrar by remember { mutableStateOf<RecetaUiData?>(null) }

    var recetaSeleccionada by remember { mutableStateOf(recetasCat[0]) }
    val listaIngredientes = remember {
        mutableStateListOf(*recetaSeleccionada.ingredientes.map { it.copy() }.toTypedArray())
    }
    var pasoIdx by remember(recetaSeleccionada.nombre) { mutableIntStateOf(0) }
    val hayPasos = recetaSeleccionada.pasos.isNotEmpty()
    val totalPasosUi = if (hayPasos) recetaSeleccionada.pasos.size else maxOf(recetaSeleccionada.totalPasos, 1)
    val pasoActualUi = if (hayPasos) pasoIdx.coerceIn(0, totalPasosUi - 1) + 1 else recetaSeleccionada.pasoActual
    val textoPasoUi = if (hayPasos) recetaSeleccionada.pasos[pasoActualUi - 1] else recetaSeleccionada.instruccion
    var notasTexto by remember(recetaSeleccionada.nombre) { mutableStateOf("") }
    var notasGuardadas by remember(recetaSeleccionada.nombre) { mutableStateOf("") }
    // Cambios míos que el servidor todavía no confirmó: el sondeo no debe deshacerlos (era la causa de que
    // un ingrediente marcado "parpadeara" o se desmarcara en el otro celular)
    val pendientes = remember { java.util.concurrent.ConcurrentHashMap<String, Pair<Boolean, Long>>() }
    var recetaPendiente by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var uriFotoPaso by remember { mutableStateOf<Uri?>(null) }
    var fotoPasoVersion by remember { mutableIntStateOf(0) }
    fun guardarFotoPaso(bmp: Bitmap?) {
        if (bmp == null) return
        ProgresoLocal.guardarFotoPaso(context, recetaSeleccionada.nombre, pasoActualUi, aJpeg(bmp, 900, 80))
        fotoPasoVersion++
    }
    val tomarFotoPaso = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = uriFotoPaso
        if (ok && u != null) guardarFotoPaso(leerReducida(context, u, 900))
    }
    val elegirFotoPaso = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) guardarFotoPaso(leerReducida(context, uri, 900))
    }
    fun lanzarFotoPaso() {
        val dir = java.io.File(context.cacheDir, "fotos").apply { mkdirs() }
        val u = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", java.io.File(dir, "paso_${System.currentTimeMillis()}.jpg")
        )
        uriFotoPaso = u
        tomarFotoPaso.launch(u)
    }

    var tiempoRestanteSegundos by remember { mutableIntStateOf(180) }
    var temporizadorCorriendo by remember { mutableStateOf(false) }

    // Al cambiar de receta (o al volver a vincular) se recupera lo que ya se terminó: primero la copia
    // del teléfono y, si no está, la de la nube. Así cerrar la sesión ya no borra la receta hecha ni su foto.
    LaunchedEffect(recetaSeleccionada.nombre) {
        val receta = recetaSeleccionada.nombre
        SustitucionContexto.ingredientes = recetaSeleccionada.ingredientes.map { it.nombre }
        SustitucionContexto.receta = recetaSeleccionada.nombre
        ultimaFotoUri = null
        recetaHecha = false
        val local = withContext(Dispatchers.IO) { ProgresoLocal.foto(context, receta) }
        if (local != null) {
            ultimaFotoUri = Uri.fromFile(local)
            recetaHecha = true
        } else {
            val nube = withContext(Dispatchers.IO) { FirebaseProgreso.cargar(receta) }
            if (nube != null && nube.hecha && nube.fotoJpeg != null) {
                val archivo = withContext(Dispatchers.IO) { ProgresoLocal.guardarFoto(context, receta, nube.fotoJpeg) }
                ultimaFotoUri = Uri.fromFile(archivo)
                recetaHecha = true
            }
        }
    }

    // Foto final recién tomada: se guarda en el teléfono, se sube a S3 con URL prefirmada y se publica en el muro.
    LaunchedEffect(fotoNueva) {
        val bmp = fotoNueva ?: return@LaunchedEffect
        val receta = recetaSeleccionada.nombre
        val ids = listaIngredientes.filter { it.completado }.map { it.id }
        val grande = withContext(Dispatchers.Default) { aJpeg(bmp, 1280, 85) }
        val miniatura = withContext(Dispatchers.Default) { aJpeg(bmp, 360, 70) }
        val archivo = withContext(Dispatchers.IO) { ProgresoLocal.guardarFoto(context, receta, grande) }
        ultimaFotoUri = Uri.fromFile(archivo)
        recetaHecha = true
        fotoNueva = null
        Toast.makeText(context, "Foto guardada y receta marcada como finalizada", Toast.LENGTH_SHORT).show()

        // Subida a S3 + muro en SEGUNDO PLANO: antes se hacía aquí mismo, pero al poner fotoNueva = null este efecto se
        // cancelaba y la foto nunca llegaba a la nube ni al muro.
        val base = serverBaseUrl
        val sid = sessionId
        val tok = sessionToken
        val vinculado = estaVinculado
        val nombreAutor = ServerConfig.nombreDispositivo()
        val appContext = context.applicationContext
        TrabajoSegundoPlano.scope.launch {
            var clave: String? = null
            var avisoS3: String? = null
            if (vinculado && sid.isNotBlank()) {
                try {
                    clave = S3UploadClient(base, sid, tok).subirFoto(grande)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: S3Exception) {
                    if (!e.noConfigurado) avisoS3 = "No se pudo subir a S3: ${e.message}"
                } catch (e: Exception) {
                    avisoS3 = "S3 no disponible (${e.javaClass.simpleName})"
                }
            }
            val guardado = withTimeoutOrNull(15_000L) { FirebaseProgreso.guardar(receta, true, ids, miniatura, clave) } ?: false
            val errorMuro = withTimeoutOrNull(15_000L) {
                FirebaseMuro.publicar(receta, nombreAutor, clave.orEmpty(), miniatura)
            } ?: "tardó demasiado: se enviará cuando haya internet"
            withContext(Dispatchers.Main) {
                val mensaje = when {
                    errorMuro == null && clave != null -> "☁️ Foto subida a S3 y publicada en el muro"
                    errorMuro == null -> "Foto publicada en el muro" + (avisoS3?.let { " ($it)" } ?: "")
                    else -> "No se pudo publicar en el muro: $errorMuro"
                }
                Toast.makeText(appContext, mensaje, Toast.LENGTH_LONG).show()
                if (!guardado && errorMuro == null) Toast.makeText(appContext, "El avance de la receta se guardará al volver la conexión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun persistSessionState() {
        sessionPrefs.edit()
            .putBoolean("vinculado", estaVinculado)
            .putString("session_id", sessionId)
            .putString("session_token", sessionToken)
            .putString("server_base_url", serverBaseUrl)
            .apply()
    }

    fun buildServerCandidates(): List<String> {
        val candidates = linkedSetOf<String>()
        if (serverBaseUrl.isNotBlank()) {
            candidates += serverBaseUrl.trimEnd('/')
        }
        listOf(
            ServerConfig.DEFAULT
        ).forEach { candidate -> candidates += candidate }
        return candidates.toList()
    }

    LaunchedEffect(Unit) {
        try {
            val uid = com.recipestudio.kitchensync.data.remote.FirebaseReportes.asegurarSesion()
            com.recipestudio.kitchensync.data.remote.FirebaseRecetas.misRecetas(uid)
                .catch { }
                .collect { lista -> recetasPropias = lista.map { it.aUi() } }
        } catch (e: Exception) {
            // sin sesión de Firebase no hay recetas propias: el catálogo base sigue funcionando
        }
    }

    // Notas personales de la receta: primero el celular y, si no hay, la nube
    LaunchedEffect(recetaSeleccionada.nombre) {
        val receta = recetaSeleccionada.nombre
        val local = ProgresoLocal.notas(context, receta)
        notasTexto = local; notasGuardadas = local
        if (local.isBlank()) {
            val nube = withTimeoutOrNull(6000L) { withContext(Dispatchers.IO) { FirebaseProgreso.cargar(receta) } }
            val texto = nube?.notas.orEmpty()
            if (texto.isNotBlank() && notasTexto.isBlank()) { notasTexto = texto; notasGuardadas = texto }
        }
    }

    LaunchedEffect(estaVinculado, sessionId, sessionToken, serverBaseUrl) {
        persistSessionState()
    }

    fun syncWithWeb(comando: String, valor: String) {
        webViewRef?.evaluateJavascript("if(window.sendSync) { window.sendSync('$comando', '$valor'); }", null)
    }

    fun mergeServerIngredientState(recipe: RecetaUiData, serverIngredients: JSONObject): List<IngredientUi> {
        return recipe.ingredientes.map { ingredient ->
            val normalizedName = ingredient.nombre.lowercase(Locale.getDefault()).replace("extra virgen", "").trim()
            val matchingServerKey = serverIngredients.keys().asSequence().firstOrNull { key ->
                key.lowercase(Locale.getDefault()).replace("extra virgen", "").trim() == normalizedName
            }
            val delServidor = if (matchingServerKey != null) {
                serverIngredients.optBoolean(matchingServerKey, ingredient.completado)
            } else {
                ingredient.completado
            }
            val mio = pendientes[ingredient.nombre]
            val completed = when {
                mio != null && System.currentTimeMillis() - mio.second < 4000L && delServidor != mio.first -> mio.first   // el servidor aún no recibe mi cambio
                else -> { if (mio != null) pendientes.remove(ingredient.nombre); delServidor }
            }
            ingredient.copy(completado = completed)
        }
    }

    // Se llama siempre desde Dispatchers.IO (antes corría en el hilo principal y la red fallaba en silencio)
    fun refrescarEstadoDesdeServidor(): Boolean {
        // Sin token no hay sesión: solo se entra escaneando el QR (antes un sondeo podía volver a vincular un celular que ya había salido)
        if (sessionToken.isBlank()) return false
        val urls = buildServerCandidates().map { "$it/estado" }

        var estadoActualizado = false
        for (urlString in urls) {
            try {
                val connection = URL(urlString).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseText)
                    val ingredientes = json.optJSONObject("ingredientes")
                    var recetaNombre = json.optString("receta", recetaSeleccionada.nombre)
                    // Si acabo de elegir otra receta y el servidor aún no lo sabe, no me regresa a la anterior
                    val miEleccion = recetaPendiente
                    if (miEleccion != null) {
                        if (System.currentTimeMillis() - miEleccion.second < 4000L && recetaNombre != miEleccion.first) recetaNombre = miEleccion.first
                        else recetaPendiente = null
                    }
                    // La estación manda la receta completa: si otro celular eligió una que este no tiene, se agrega al catálogo
                    json.optJSONObject("recetaDetalle")?.let { det ->
                        val n = det.optString("nombre")
                        if (n.isNotBlank() && catalogoActual.none { it.nombre == n } && recetasRemotas.none { it.nombre == n }) recetasRemotas.add(recetaDesdeDetalle(det))
                    }
                    val recetaMatch = (catalogoActual + recetasRemotas).find { it.nombre == recetaNombre }
                    val targetRecipe = recetaMatch ?: recetaSeleccionada
                    if (recetaMatch != null && recetaMatch.nombre != recetaSeleccionada.nombre) {
                        recetaSeleccionada = recetaMatch
                    }

                    if (ingredientes != null && json.optString("receta") == targetRecipe.nombre) {
                        val merged = mergeServerIngredientState(targetRecipe, ingredientes)
                        listaIngredientes.clear()
                        listaIngredientes.addAll(merged)
                    } else {
                        val ingredientesReceta = targetRecipe.ingredientes.map { it.copy() }
                        listaIngredientes.clear()
                        listaIngredientes.addAll(ingredientesReceta)
                    }

                    val nuevoTimer = json.optInt("timerSeconds", tiempoRestanteSegundos)
                    tiempoRestanteSegundos = nuevoTimer
                    temporizadorCorriendo = json.optBoolean("timerRunning", temporizadorCorriendo)
                    // La sesión NO se copia de la estación: este celular sigue vinculado solo si la estación sigue conectada
                    // con la MISMA sesión que se escaneó (si se reinició o se unió otro celular, vuelve al QR).
                    val mismaSesion = json.optString("sessionId", sessionId) == sessionId
                    estaVinculado = json.optBoolean("conectado", estaVinculado) && mismaSesion
                    val connectedBaseUrl = json.optString("serverBaseUrl", serverBaseUrl)
                    if (connectedBaseUrl.isNotBlank()) {
                        serverBaseUrl = ServerConfig.normalizar(connectedBaseUrl)
                    }

                    val ingredientesActuales = listaIngredientes
                    recetaFinalizada = ingredientesActuales.isNotEmpty() && ingredientesActuales.all { it.completado }
                    estadoActualizado = true
                }
                connection.disconnect()
                if (estadoActualizado) break
            } catch (_: Exception) {
            }
        }
        return estadoActualizado
    }

    fun enviarEventoServer(evento: Map<String, Any>) {
        scope.launch(Dispatchers.IO) {
            try {
                val payload = JSONObject(evento)
                val urls = buildServerCandidates().map { "$it/evento" }

                var ultimoError: Exception? = null
                var enviadoConExito = false
                for (urlString in urls) {
                    try {
                        val url = URL(urlString)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        connection.doOutput = true
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000
                        val output = connection.outputStream
                        output.write(payload.toString().toByteArray(Charsets.UTF_8))
                        output.flush()
                        output.close()
                        connection.responseCode
                        connection.disconnect()
                        ultimoError = null
                        enviadoConExito = true
                    } catch (e: Exception) {
                        ultimoError = e
                    }
                    if (enviadoConExito) break
                }

                if (ultimoError != null) {
                    throw ultimoError
                }
            } catch (e: Exception) {
                Log.e("Sync", "Error al enviar evento", e)
            }
        }
    }

    /** Elegir receta: se manda COMPLETA a la estación para que la PC y los demás celulares vean los mismos ingredientes. */
    fun anunciarReceta(receta: RecetaUiData) {
        recetaPendiente = receta.nombre to System.currentTimeMillis()
        syncWithWeb("CHANGE_RECIPE", receta.nombre)
        enviarEventoServer(
            mapOf(
                "type" to "CHANGE_RECIPE", "sessionId" to sessionId, "token" to sessionToken,
                "recipeName" to receta.nombre, "recipe" to receta.aJson(listaIngredientes.toList())
            )
        )
    }

    LaunchedEffect(Unit) {
        if (!tienePermisoCamara) {
            launcherPermisos.launch(Manifest.permission.CAMERA)
        }
        if (estaVinculado && sessionId.isNotBlank()) {
            withContext(Dispatchers.IO) { refrescarEstadoDesdeServidor() }
        }
    }

    LaunchedEffect(estaVinculado, sessionId, sessionToken) {
        persistSessionState()
    }

    LaunchedEffect(estaVinculado) {
        if (!estaVinculado) return@LaunchedEffect
        while (estaVinculado) {
            val conectado = withContext(Dispatchers.IO) { refrescarEstadoDesdeServidor() }
            if (conectado) {
                delay(1500L)
            } else {
                // La estación cambió de IP o no responde: buscarla sola en la red
                ServerDiscovery.buscar()?.let { nueva ->
                    if (nueva != serverBaseUrl) {
                        serverBaseUrl = nueva
                        ServerConfig.guardar(context, nueva)
                    }
                }
                delay(2500L)
            }
        }
    }

    LaunchedEffect(temporizadorCorriendo) {
        while (temporizadorCorriendo && tiempoRestanteSegundos > 0) {
            delay(1000L)
            tiempoRestanteSegundos--
            syncWithWeb("TIMER_TICK", tiempoRestanteSegundos.toString())
            enviarEventoServer(
                mapOf(
                    "type" to "TIMER_TICK",
                    "sessionId" to sessionId,
                    "token" to sessionToken,
                    "timerSeconds" to tiempoRestanteSegundos
                )
            )
        }

        if (tiempoRestanteSegundos <= 0 && temporizadorCorriendo) {
            temporizadorCorriendo = false
            AlertaTemporizador.sonar(context)
            Toast.makeText(context, "⏰ La receta ya terminó. ¡Tiempo finalizado!", Toast.LENGTH_LONG).show()
        }
    }

    // Modo cocina: la pantalla no se apaga mientras el celular está vinculado a una estación
    DisposableEffect(estaVinculado) {
        val ventana = (context as? android.app.Activity)?.window
        if (estaVinculado) ventana?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { ventana?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val min = tiempoRestanteSegundos / 60
    val seg = tiempoRestanteSegundos % 60
    val tiempoTexto = String.format(Locale.getDefault(), "%02d:%02d", min, seg)

    Box(
        modifier = Modifier.size(1.dp)
    ) {
        AndroidView(factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                loadDataWithBaseURL("https://localhost", """
                    <html><body><script>
                        const channel = new BroadcastChannel('kitchen_sync_channel');
                        window.sendSync = function(cmd, val) {
                            channel.postMessage({ type: cmd, value: val });
                        }
                    </script></body></html>
                """.trimIndent(), "text/html", "UTF-8", null)
                webViewRef = this
            }
        })
    }

    if (!estaVinculado) {
        Surface(modifier = Modifier.fillMaxSize(), color = CreamBg) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(PrimaryOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "KitchenSync - Escanear QR",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Apunta con la cámara de tu celular al código QR visible en la computadora para conectar la sesión.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(3.dp, PrimaryOrange, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (tienePermisoCamara) {
                        RealCameraPreview(onQrDetected = { qrValue ->
                            // la cámara detecta el mismo QR en cada cuadro: solo se procesa uno a la vez
                            if (conectando) return@RealCameraPreview
                            conectando = true
                            scope.launch(Dispatchers.IO) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Conectando con la estación… puede tardar si el servidor estaba dormido", Toast.LENGTH_LONG).show()
                                }
                                try {
                                    val cleanUrl = qrValue.trim()
                                    val baseUrl = cleanUrl.substringBefore("/conectar", cleanUrl)
                                    val qrSessionId = cleanUrl.substringAfter("sessionId=", cleanUrl)
                                        .substringBefore("&")
                                        .ifBlank { "demo-session" }
                                    val token = cleanUrl.substringAfter("token=", cleanUrl)
                                        .substringBefore("&")
                                        .ifBlank { "demo-token" }

                                    val connectUrl = if (cleanUrl.contains("/conectar")) {
                                        cleanUrl
                                    } else {
                                        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
                                        "$normalizedBase/conectar?sessionId=$qrSessionId&token=$token"
                                    }
                                    // El QR puede traer la IP interna del contenedor (p. ej. 10.x.x.x:10000) si el servidor
                                    // no conoce su URL pública: se conecta SIEMPRE a la dirección normalizada (la de Render).
                                    val baseSegura = ServerConfig.normalizar(connectUrl.substringBefore("/conectar"))
                                    serverBaseUrl = baseSegura

                                    val url = URL(baseSegura + "/conectar" + connectUrl.substringAfter("/conectar", ""))
                                    val connection = url.openConnection() as HttpURLConnection
                                    connection.requestMethod = "GET"
                                    connection.connectTimeout = 30000   // el servidor gratis puede tardar ~1 min en despertar
                                    connection.readTimeout = 30000

                                    val responseCode = connection.responseCode
                                    connection.disconnect()

                                    if (responseCode == 200) {
                                        withContext(Dispatchers.Main) {
                                            estaVinculado = true
                                            sessionId = qrSessionId
                                            sessionToken = token
                                            persistSessionState()
                                            Toast.makeText(context, "¡Vinculado con la Web! sesión=$qrSessionId", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Error servidor: $responseCode", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("Sync", "Error al conectar", e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    conectando = false
                                }
                            }
                        })
                    } else {
                        Text(text = "Se requiere permiso de cámara", color = Color.Red, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(onClick = onSoporte, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.SupportAgent, contentDescription = null, tint = PrimaryOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Soy soporte (entrar al panel)", color = PrimaryOrange, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "🍳 Catálogo de Recetas", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedButton(
                        onClick = { scope.launch { drawerState.close() }; creandoReceta = true },
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    ) { Text("➕ Crear mi receta", color = PrimaryOrange, fontWeight = FontWeight.Bold) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    recetasCat.forEach { receta ->
                        NavigationDrawerItem(
                            label = { Text(receta.nombre, fontWeight = FontWeight.SemiBold) },
                            badge = if (receta.propiaId != null) ({
                                Row {
                                    Text("✏️", modifier = Modifier.clickable {
                                        scope.launch { drawerState.close() }
                                        editandoReceta = com.recipestudio.kitchensync.data.remote.RecetaPropia(
                                            id = receta.propiaId, nombre = receta.nombre, categoria = receta.categoria.let { if (it == "MI RECETA") "" else it },
                                            tiempo = receta.tiempo.let { if (it == "—") "" else it }, dificultad = receta.dificultad,
                                            porciones = receta.porciones.let { if (it == "—") "" else it }, chefTip = receta.chefTip.let { if (it == "Receta propia") "" else it },
                                            pasos = receta.pasos, ingredientes = receta.ingredientes.map { it.nombre to it.cantidad }
                                        )
                                    }.padding(horizontal = 6.dp))
                                    Text("🗑", modifier = Modifier.clickable { recetaABorrar = receta }.padding(horizontal = 6.dp))
                                }
                            }) else null,
                            selected = recetaSeleccionada.nombre == receta.nombre,
                            onClick = {
                                recetaSeleccionada = receta
                                listaIngredientes.clear()
                                listaIngredientes.addAll(receta.ingredientes.map { it.copy() })
                                anunciarReceta(receta)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                    }
                }
            }
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = CreamBg) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White)
                                        .border(1.dp, Color.LightGray, RoundedCornerShape(10.dp))
                                        .clickable { scope.launch { drawerState.open() } },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menú", tint = TextDark)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = "RecipeStudio", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            }
                            // Estado de la sesión + salida: el usuario puede dejar la estación sin perder sus reportes ni recetas hechas
                            Box {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SuccessGreen)
                                        .clickable { menuSesion = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(text = "🟢 Conectado  ▾", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                DropdownMenu(expanded = menuSesion, onDismissRequest = { menuSesion = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Sincronizado con la estación", fontSize = 12.sp) },
                                        onClick = {}, enabled = false
                                    )
                                    DropdownMenuItem(
                                        text = { Text("🚪 Salir de la sesión", fontWeight = FontWeight.SemiBold) },
                                        onClick = { menuSesion = false; confirmarSalir = true }
                                    )
                                }
                            }
                            if (confirmarSalir) {
                                AlertDialog(
                                    onDismissRequest = { confirmarSalir = false },
                                    title = { Text("¿Salir de la sesión?") },
                                    text = { Text("Dejarás de controlar la estación desde este celular. Tus reportes y tus recetas terminadas con su foto se conservan. Para volver, escanea el QR otra vez.") },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                confirmarSalir = false
                                                temporizadorCorriendo = false
                                                recetaSecretaDesbloqueada = false
                                                estaVinculado = false
                                                sessionId = ""
                                                sessionToken = ""
                                                persistSessionState()
                                                Toast.makeText(context, "Saliste de la sesión. Tus reportes y recetas siguen guardados.", Toast.LENGTH_LONG).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                                        ) { Text("Salir") }
                                    },
                                    dismissButton = { TextButton(onClick = { confirmarSalir = false }) { Text("Cancelar") } }
                                )
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = recetaSeleccionada.categoria, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryOrange)
                            Text(text = recetaSeleccionada.nombre, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text(text = "⏱ ${recetaSeleccionada.tiempo} • ${recetaSeleccionada.dificultad} • ${recetaSeleccionada.porciones}", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Progreso de la receta", fontSize = 11.sp, color = Color.Gray)
                                Text(text = "$pasoActualUi de $totalPasosUi pasos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { pasoActualUi.toFloat() / totalPasosUi },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = PrimaryOrange,
                                trackColor = Color.LightGray.copy(alpha = 0.4f)
                            )
                        }
                    }

                    item {
                        // Tarjeta del paso: foto propia si la hay; si no, una ilustración según lo que pide el paso
                        val fotoPaso = remember(recetaSeleccionada.nombre, pasoActualUi, fotoPasoVersion) {
                            ProgresoLocal.fotoPaso(context, recetaSeleccionada.nombre, pasoActualUi)?.let { BitmapFactory.decodeFile(it.path) }
                        }
                        val paletas = listOf(
                            listOf(Color(0xFFCF5A3A), Color(0xFFE4A14E)), listOf(Color(0xFF5C8F66), Color(0xFFA8C97F)),
                            listOf(Color(0xFF2A2B2E), Color(0xFF7A6455)), listOf(Color(0xFFB8453A), Color(0xFFE88D67))
                        )
                        Card(modifier = Modifier.fillMaxWidth().height(210.dp), shape = RoundedCornerShape(20.dp)) {
                            Box(Modifier.fillMaxSize().background(Brush.linearGradient(paletas[(pasoActualUi - 1).mod(paletas.size)]))) {
                                if (fotoPaso != null) {
                                    Image(bitmap = fotoPaso.asImageBitmap(), contentDescription = "Foto del paso", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Text(iconoPaso(textoPasoUi), fontSize = 78.sp, modifier = Modifier.align(Alignment.Center))
                                }
                                Box(modifier = Modifier.padding(14.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text("PASO ${String.format(Locale.getDefault(), "%02d", pasoActualUi)} / $totalPasosUi", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Row(modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BotonSobreFoto("📷 Foto") { lanzarFotoPaso() }
                                    BotonSobreFoto("🖼") { elegirFotoPaso.launch("image/*") }
                                    if (fotoPaso != null) BotonSobreFoto("✖") {
                                        ProgresoLocal.borrarFotoPaso(context, recetaSeleccionada.nombre, pasoActualUi)
                                        fotoPasoVersion++
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(text = "PASO ACTUAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryOrange)
                            Text(text = textoPasoUi, fontSize = 16.sp, color = TextDark, lineHeight = 24.sp)
                            if (hayPasos && totalPasosUi > 1) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { pasoIdx = (pasoIdx - 1).coerceAtLeast(0) }, enabled = pasoActualUi > 1, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text("← Anterior") }
                                    Button(onClick = { pasoIdx = (pasoIdx + 1).coerceAtMost(totalPasosUi - 1) }, enabled = pasoActualUi < totalPasosUi, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange), modifier = Modifier.weight(1f)) { Text("Siguiente →") }
                                }
                            }

                            // Notas personales: para acordarse de lo que se cambió la próxima vez que se cocine
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("📝 MIS NOTAS DE ESTA RECETA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryOrange)
                                    OutlinedTextField(
                                        value = notasTexto, onValueChange = { notasTexto = it.take(600) }, minLines = 2,
                                        placeholder = { Text("Ej. Le puse 1 g más de sal y quedó perfecto", fontSize = 13.sp) },
                                        supportingText = { Text("${notasTexto.length}/600", fontSize = 10.sp) }, modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        enabled = notasTexto != notasGuardadas,
                                        onClick = {
                                            val receta = recetaSeleccionada.nombre
                                            val texto = notasTexto.trim()
                                            ProgresoLocal.guardarNotas(context, receta, texto)
                                            notasTexto = texto; notasGuardadas = texto
                                            Toast.makeText(context, "Notas guardadas ✔", Toast.LENGTH_SHORT).show()
                                            scope.launch(Dispatchers.IO) { FirebaseProgreso.guardarNotas(receta, texto) }
                                        },
                                        shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                    ) { Text(if (notasTexto == notasGuardadas && notasTexto.isNotBlank()) "Guardadas ✔" else "Guardar notas") }
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = LightOrangeCard)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(text = "👨‍🍳", fontSize = 18.sp)
                                    Column {
                                        Text(text = "CONSEJO DEL CHEF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryOrange)
                                        Text(text = recetaSeleccionada.chefTip, fontSize = 12.sp, color = TextDark)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "SUSTITUCIÓN DE INGREDIENTE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryOrange)
                                    if (recetaSecretaDesbloqueada) {
                                        Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(SuccessGreen.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text("Secreto desbloqueado", fontSize = 10.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Text(
                                    text = "¿No tienes un ingrediente? Tómale foto a lo que sí tienes y una IA te dice cuánto cambiaría el sabor.",
                                    fontSize = 13.sp,
                                    color = TextDark,
                                    lineHeight = 18.sp
                                )
                                OutlinedButton(
                                    onClick = onIrSustitutos,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("📷 Sustituir con foto", color = PrimaryOrange, fontWeight = FontWeight.Bold) }

                                Button(
                                    onClick = {
                                        val activity = context as? FragmentActivity
                                        if (activity == null) {
                                            Toast.makeText(context, "Biometría no disponible en esta vista", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        BiometricAuth(activity).autenticarChef(
                                            onExito = {
                                                recetaSecretaDesbloqueada = true
                                                Toast.makeText(context, "Receta secreta desbloqueada", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { mensaje ->
                                                Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkCard)
                                ) {
                                    Text(text = if (recetaSecretaDesbloqueada) "Receta secreta abierta" else "Desbloquear receta secreta")
                                }
                            }
                        }
                    }

                    item {
                        val completadosCount = listaIngredientes.count { it.completado }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(text = "MISE EN PLACE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryOrange)
                                        Text(text = "Ingredientes", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                    }
                                    Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFE8F2EA)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                        Text(text = "$completadosCount/${listaIngredientes.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                    }
                                }

                                TextButton(onClick = onIrSustitutos, contentPadding = PaddingValues(0.dp)) {
                                    Text("¿Te falta algo? Sustituir con foto 📷", color = PrimaryOrange, fontSize = 12.sp)
                                }

                                listaIngredientes.forEachIndexed { index, ing ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            val nuevoEstado = !ing.completado
                                            pendientes[ing.nombre] = nuevoEstado to System.currentTimeMillis()
                                            listaIngredientes[index] = ing.copy(completado = nuevoEstado)
                                            recetaFinalizada = listaIngredientes.all { it.completado }

                                            enviarEventoServer(
                                                mapOf(
                                                    "type" to "INGREDIENT_TOGGLE",
                                                    "sessionId" to sessionId,
                                                    "token" to sessionToken,
                                                    "ingredient" to ing.nombre,
                                                    "completed" to nuevoEstado
                                                )
                                            )
                                            syncWithWeb("INGREDIENT_TOGGLE", ing.nombre)
                                        },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(
                                                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                                                    .background(if (ing.completado) SuccessGreen else Color.Transparent)
                                                    .border(1.dp, if (ing.completado) SuccessGreen else Color.Gray, RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (ing.completado) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                            Text(text = ing.nombre, fontSize = 14.sp, color = TextDark)
                                        }
                                        Text(text = ing.cantidad, fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = tiempoTexto, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    IconButton(
                                        onClick = {
                                            temporizadorCorriendo = false
                                            tiempoRestanteSegundos = 180
                                            syncWithWeb("TIMER_RESET", "180")
                                            enviarEventoServer(
                                                mapOf(
                                                    "type" to "TIMER_RESET",
                                                    "sessionId" to sessionId,
                                                    "token" to sessionToken,
                                                    "timerSeconds" to 180
                                                )
                                            )
                                        },
                                        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.1f))
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Reiniciar", tint = Color.White)
                                    }
                                    Button(
                                        onClick = {
                                            temporizadorCorriendo = !temporizadorCorriendo
                                            syncWithWeb("TIMER_TOGGLE", temporizadorCorriendo.toString())
                                            enviarEventoServer(
                                                mapOf(
                                                    "type" to "TIMER_TOGGLE",
                                                    "sessionId" to sessionId,
                                                    "token" to sessionToken,
                                                    "running" to temporizadorCorriendo
                                                )
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TimerYellow),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = TextDark)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (temporizadorCorriendo) "Pausar" else "Iniciar", color = TextDark, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (recetaFinalizada || recetaHecha) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(text = if (recetaHecha) "RECETA HECHA" else "RECETA FINALIZADA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                    Text(text = if (recetaHecha) "La receta ya quedó terminada y la foto quedó guardada. Puedes pasar a la siguiente receta." else "Todos los ingredientes están listos. Puedes guardar evidencia y desbloquear la siguiente receta.", fontSize = 13.sp, color = TextDark)

                                    if (ultimaFotoUri != null) {
                                        val bitmap = remember(ultimaFotoUri) {
                                            BitmapFactory.decodeFile(ultimaFotoUri!!.path)
                                        }
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "Foto final de la receta",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(180.dp)
                                                    .clip(RoundedCornerShape(14.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (ultimaFotoUri == null) {
                                                lanzarFotoFinal()
                                            } else {
                                                val indiceActual = recetasCat.indexOfFirst { it.nombre == recetaSeleccionada.nombre }
                                                val siguiente = recetasCat.getOrNull(indiceActual + 1) ?: recetasCat.first()
                                                recetaSeleccionada = siguiente
                                                listaIngredientes.clear()
                                                listaIngredientes.addAll(siguiente.ingredientes.map { it.copy() })
                                                recetaFinalizada = false
                                                recetaHecha = false
                                                ultimaFotoUri = null
                                                tiempoRestanteSegundos = 180
                                                temporizadorCorriendo = false
                                                anunciarReceta(siguiente)
                                                Toast.makeText(context, "Siguiente receta desbloqueada", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (ultimaFotoUri != null) SuccessGreen else PrimaryOrange),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(if (ultimaFotoUri != null) "Siguiente receta" else "Tomar foto final")
                                    }

                                    if (ultimaFotoUri != null) {
                                        OutlinedButton(onClick = { lanzarFotoFinal() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                            Text("📷 Cambiar foto del platillo")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ----- Recetas propias: crear / editar / borrar -----
    if (creandoReceta || editandoReceta != null) {
        CrearRecetaDialog(
            inicial = editandoReceta,
            onCerrar = { creandoReceta = false; editandoReceta = null },
            onGuardar = { r ->
                // sin internet Firestore deja la escritura en cola: tras unos segundos se da por guardada
                withTimeoutOrNull(6000L) { com.recipestudio.kitchensync.data.remote.FirebaseRecetas.guardar(r) } ?: true
            }
        )
    }
    recetaABorrar?.let { r ->
        AlertDialog(
            onDismissRequest = { recetaABorrar = null },
            title = { Text("Eliminar receta") },
            text = { Text("¿Eliminar \"${r.nombre}\"? Tus notas de esa receta se conservan en el celular.") },
            confirmButton = {
                TextButton(onClick = {
                    recetaABorrar = null
                    val id = r.propiaId
                    if (id != null) scope.launch(Dispatchers.IO) { com.recipestudio.kitchensync.data.remote.FirebaseRecetas.eliminar(id) }
                    if (recetaSeleccionada.nombre == r.nombre) {
                        val primera = recetasBase.first()
                        recetaSeleccionada = primera
                        listaIngredientes.clear(); listaIngredientes.addAll(primera.ingredientes.map { it.copy() })
                    }
                }) { Text("Eliminar", color = Color(0xFFB3391B)) }
            },
            dismissButton = { TextButton(onClick = { recetaABorrar = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun BotonSobreFoto(texto: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
    ) { Text(texto, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
}

@Suppress("UnsafeOptInUsageError")
@Composable
fun RealCameraPreview(onQrDetected: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var yaDetectado by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val barcodeScanner = BarcodeScanning.getClient()
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !yaDetectado) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            barcodeScanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val qrValue = barcode.rawValue
                                        if (qrValue != null && !yaDetectado) {
                                            yaDetectado = true
                                            onQrDetected(qrValue)
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (exc: Exception) {
                        Log.e("Camera", "Error al vincular cámara", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}