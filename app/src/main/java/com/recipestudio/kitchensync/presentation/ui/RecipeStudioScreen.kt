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
import com.recipestudio.kitchensync.data.remote.FirebaseProgreso
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
    val ingredientes: List<IngredientUi>
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
        mutableStateOf(sessionPrefs.getString("server_base_url", ServerConfig.DEFAULT) ?: ServerConfig.DEFAULT)
    }
    var recetaFinalizada by remember { mutableStateOf(false) }

    var ultimaFotoUri by remember { mutableStateOf<Uri?>(null) }
    var recetaHecha by remember { mutableStateOf(false) }

    var fotoNueva by remember { mutableStateOf<Bitmap?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) fotoNueva = bitmap
    }

    val sustitucionAzucar = remember {
        InsumoSubstitutionManager.obtenerSustituto("Azúcar")
            ?: InsumoSubstitutionManager.obtenerSustituto("Azúcar Blanca")
    }

    val recetasCat = remember {
        listOf(
            RecetaUiData(
                nombre = "Pasta al pomodoro",
                categoria = "COCINA ITALIANA • RECETA FAMILIAR",
                tiempo = "35 min",
                dificultad = "Dificultad media",
                porciones = "4 porciones",
                pasoActual = 3,
                totalPasos = 3,
                instruccion = "Pasa la pasta a la sartén, añade un poco del agua de cocción y mezcla energéticamente. Termina con albahaca y aceite de oliva.",
                chefTip = "Conserva una taza del agua de cocción. El almidón ayudará a que la salsa se agarre a la pasta.",
                ingredientes = listOf(
                    IngredientUi("1", "Spaghetti artesanal", "400 g", true),
                    IngredientUi("2", "Tomates maduros", "6", true),
                    IngredientUi("3", "Ajo fresco", "2 dientes", true),
                    IngredientUi("4", "Albahaca fresca", "1 manojo", false),
                    IngredientUi("5", "Aceite de oliva extra virgen", "45 ml", false),
                    IngredientUi("6", "Sal de mar y pimienta", "Al gusto", false)
                )
            ),
            RecetaUiData(
                nombre = "Enchiladas Suizas",
                categoria = "COCINA MEXICANA • TRADICIONAL",
                tiempo = "45 min",
                dificultad = "Dificultad alta",
                porciones = "3 porciones",
                pasoActual = 2,
                totalPasos = 4,
                instruccion = "Envuelve el pollo deshebrado en tortillas calientes, baña con la salsa verde cremosa a base de tomatillo y gratina con abundante queso manchego.",
                chefTip = "Usa crema ácida fresca justo al salir del horno para realzar la textura cremosa.",
                ingredientes = listOf(
                    IngredientUi("1", "Tortillas de maíz", "12 pzas", true),
                    IngredientUi("2", "Pollo cocido deshebrado", "500 g", true),
                    IngredientUi("3", "Tomatillo verde", "8 pzas", false),
                    IngredientUi("4", "Queso manchego rallado", "200 g", false)
                )
            )
        )
    }

    var recetaSeleccionada by remember { mutableStateOf(recetasCat[0]) }
    val listaIngredientes = remember(recetaSeleccionada) {
        mutableStateListOf(*recetaSeleccionada.ingredientes.map { it.copy() }.toTypedArray())
    }

    var tiempoRestanteSegundos by remember { mutableIntStateOf(180) }
    var temporizadorCorriendo by remember { mutableStateOf(false) }

    // Al cambiar de receta (o al volver a vincular) se recupera lo que ya se terminó: primero la copia
    // del teléfono y, si no está, la de la nube. Así cerrar la sesión ya no borra la receta hecha ni su foto.
    LaunchedEffect(recetaSeleccionada.nombre) {
        val receta = recetaSeleccionada.nombre
        SustitucionContexto.ingredientes = recetaSeleccionada.ingredientes.map { it.nombre }
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

    // Foto final recién tomada: se guarda en el teléfono y en la nube.
    LaunchedEffect(fotoNueva) {
        val bmp = fotoNueva ?: return@LaunchedEffect
        val receta = recetaSeleccionada.nombre
        val ids = listaIngredientes.filter { it.completado }.map { it.id }
        val bytes = java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        val archivo = withContext(Dispatchers.IO) { ProgresoLocal.guardarFoto(context, receta, bytes) }
        ultimaFotoUri = Uri.fromFile(archivo)
        recetaHecha = true
        fotoNueva = null
        Toast.makeText(context, "Foto guardada y receta marcada como finalizada", Toast.LENGTH_SHORT).show()
        val enNube = withContext(Dispatchers.IO) { FirebaseProgreso.guardar(receta, true, ids, bytes) }
        if (!enNube) Toast.makeText(context, "Guardada en el celular; se subirá a la nube cuando haya internet", Toast.LENGTH_LONG).show()
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
            val completed = if (matchingServerKey != null) {
                serverIngredients.optBoolean(matchingServerKey, ingredient.completado)
            } else {
                ingredient.completado
            }
            ingredient.copy(completado = completed)
        }
    }

    // Se llama siempre desde Dispatchers.IO (antes corría en el hilo principal y la red fallaba en silencio)
    fun refrescarEstadoDesdeServidor(): Boolean {
        val urls = buildServerCandidates().map { "$it/estado" }

        var estadoActualizado = false
        for (urlString in urls) {
            try {
                val connection = URL(urlString).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseText)
                    val ingredientes = json.optJSONObject("ingredientes")
                    val recetaNombre = json.optString("receta", recetaSeleccionada.nombre)
                    val recetaMatch = recetasCat.find { it.nombre == recetaNombre }
                    val targetRecipe = recetaMatch ?: recetaSeleccionada
                    if (recetaMatch != null) {
                        recetaSeleccionada = recetaMatch
                    }

                    if (ingredientes != null) {
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
                    sessionId = json.optString("sessionId", sessionId)
                    sessionToken = json.optString("token", sessionToken)
                    estaVinculado = json.optBoolean("conectado", estaVinculado)
                    val connectedBaseUrl = json.optString("serverBaseUrl", serverBaseUrl)
                    if (connectedBaseUrl.isNotBlank()) {
                        serverBaseUrl = connectedBaseUrl.trimEnd('/')
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
                        connection.connectTimeout = 4000
                        connection.readTimeout = 4000
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
            Toast.makeText(context, "⏰ La receta ya terminó. ¡Tiempo finalizado!", Toast.LENGTH_LONG).show()
        }
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
                            scope.launch(Dispatchers.IO) {
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
                                    serverBaseUrl = connectUrl.substringBefore("/conectar")

                                    val url = URL(connectUrl)
                                    val connection = url.openConnection() as HttpURLConnection
                                    connection.requestMethod = "GET"
                                    connection.connectTimeout = 4000
                                    connection.readTimeout = 4000

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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    recetasCat.forEach { receta ->
                        NavigationDrawerItem(
                            label = { Text(receta.nombre, fontWeight = FontWeight.SemiBold) },
                            selected = recetaSeleccionada.nombre == receta.nombre,
                            onClick = {
                                recetaSeleccionada = receta
                                listaIngredientes.clear()
                                listaIngredientes.addAll(receta.ingredientes.map { it.copy() })
                                syncWithWeb("CHANGE_RECIPE", receta.nombre)
                                enviarEventoServer(
                                    mapOf(
                                        "type" to "CHANGE_RECIPE",
                                        "sessionId" to sessionId,
                                        "token" to sessionToken,
                                        "recipeName" to receta.nombre
                                    )
                                )
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
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
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SuccessGreen)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(text = "🟢 Sincronizado", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                                Text(text = "${recetaSeleccionada.pasoActual} de ${recetaSeleccionada.totalPasos} pasos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { recetaSeleccionada.pasoActual.toFloat() / recetaSeleccionada.totalPasos },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = PrimaryOrange,
                                trackColor = Color.LightGray.copy(alpha = 0.4f)
                            )
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier.padding(16.dp).size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "PASO", fontSize = 8.sp, color = Color.White)
                                        Text(text = String.format(Locale.getDefault(), "%02d", recetaSeleccionada.pasoActual), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(text = "PASO ACTUAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryOrange)
                            Text(text = recetaSeleccionada.instruccion, fontSize = 14.sp, color = TextDark, lineHeight = 20.sp)

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
                                                takePictureLauncher.launch(null)
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
                                                syncWithWeb("CHANGE_RECIPE", siguiente.nombre)
                                                enviarEventoServer(
                                                    mapOf(
                                                        "type" to "CHANGE_RECIPE",
                                                        "sessionId" to sessionId,
                                                        "token" to sessionToken,
                                                        "recipeName" to siguiente.nombre
                                                    )
                                                )
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
                                        Text("Foto guardada: ${ultimaFotoUri!!.lastPathSegment}", fontSize = 11.sp, color = Color.Gray)
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