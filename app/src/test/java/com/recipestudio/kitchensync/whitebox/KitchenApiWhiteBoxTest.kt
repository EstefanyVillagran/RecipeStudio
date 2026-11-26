package com.recipestudio.kitchensync.whitebox

import com.recipestudio.kitchensync.network.ApiException
import com.recipestudio.kitchensync.network.KitchenApi
import com.recipestudio.kitchensync.network.SesionQr
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

/** Práctica 11 - pruebas HTTP aisladas con MockWebServer. */
class KitchenApiWhiteBoxTest {
    private lateinit var server: MockWebServer
    private var respuestaCodigo = 200
    private var respuestaCuerpo = ""
    private var ultimaPeticion: Triple<String, String, String>? = null
    private val sesion = SesionQr("station-1234", "token-secreto", "Pixel 7")

    private fun api(): KitchenApi {
        server.enqueue(MockResponse().setResponseCode(respuestaCodigo).setBody(respuestaCuerpo))
        return KitchenApi(server.url("").toString().trimEnd('/'), sesion)
    }

    @Before fun arrancar() {
        server = MockWebServer()
        server.start()
    }

    @After fun detener() = server.shutdown()

    private fun capturarPeticion() {
        val peticion = server.takeRequest()
        ultimaPeticion = Triple(peticion.method ?: "", peticion.path ?: "", peticion.body.readUtf8())
    }

    private fun codigoDeError(bloque: suspend () -> Unit): ApiException =
        runCatching { runBlocking { bloque() } }.exceptionOrNull() as ApiException

    private val reporteJson = """{"id":"REP-001","titulo":"T","descripcion":"D","categoria":"Otro","prioridad":"Media",
        "estado":"Pendiente","nombre":"Pixel","asignadoA":null,
        "notas":[{"autor":"agente","texto":"En revisión","hora":"10:00:00"}]}"""

    // ---- A: éxito ----
    @Test fun a_200_con_reportes_se_convierten_a_objetos() {
        respuestaCuerpo = """{"reportes":[$reporteJson]}"""
        val lista = runBlocking { api().misReportes() }
        assertEquals(1, lista.size)
        assertEquals("REP-001", lista[0].id)
        assertNull(lista[0].asignadoA)                    // rama isNull("asignadoA") == true
        assertEquals("En revisión", lista[0].notas[0].texto)
    }

    @Test fun a_200_asignadoA_con_valor() {
        respuestaCuerpo = """{"reportes":[${reporteJson.replace("\"asignadoA\":null", "\"asignadoA\":\"agente\"")}]}"""
        assertEquals("agente", runBlocking { api().misReportes() }[0].asignadoA)
    }

    // ---- B: cuerpo vacío o ilegible con 200 -> lista vacía (no lanza) ----
    @Test fun b_200_con_lista_vacia() { respuestaCuerpo = """{"reportes":[]}"""; assertTrue(runBlocking { api().misReportes() }.isEmpty()) }
    @Test fun b_200_sin_cuerpo() { respuestaCuerpo = ""; assertTrue(runBlocking { api().misReportes() }.isEmpty()) }
    @Test fun b_200_con_texto_que_no_es_json() { respuestaCuerpo = "<html>hola</html>"; assertTrue(runBlocking { api().misReportes() }.isEmpty()) }

    // ---- C y D: errores HTTP ----
    @Test fun c_401_con_mensaje_del_servidor() {
        respuestaCodigo = 401; respuestaCuerpo = """{"error":"Vincula tu celular con el QR"}"""
        val e = codigoDeError { api().misReportes() }
        assertEquals(401, e.status); assertEquals("Vincula tu celular con el QR", e.message)
    }
    @Test fun d_401_sin_cuerpo_usa_mensaje_por_defecto() {
        respuestaCodigo = 401; respuestaCuerpo = ""
        assertEquals("Vincula tu celular con el QR", codigoDeError { api().misReportes() }.message)
    }
    @Test fun d_403_sin_cuerpo() {
        respuestaCodigo = 403; respuestaCuerpo = ""
        assertEquals("No tienes permiso para esta operación", codigoDeError { api().misReportes() }.message)
    }
    @Test fun d_500_con_cuerpo_no_json() {
        respuestaCodigo = 500; respuestaCuerpo = "Traceback (most recent call last)"
        val e = codigoDeError { api().misReportes() }
        assertEquals(500, e.status); assertEquals("Error del servidor (500)", e.message)   // no se filtra el cuerpo al usuario
    }

    // ---- E: sin conexión (puerto cerrado) ----
    @Test fun e_sin_conexion_lanza_ApiException_con_estado_0() {
        val puertoLibre = ServerSocket(0).use { it.localPort }
        val e = codigoDeError { KitchenApi("http://127.0.0.1:$puertoLibre", sesion).misReportes() }
        assertEquals(0, e.status); assertEquals("No se pudo conectar con la estación", e.message)
    }

    // ---- F: GET sin cuerpo y POST con cuerpo; el POST no pone el token en la URL ----
    @Test fun f_get_lleva_la_sesion_en_la_consulta() {
        respuestaCuerpo = """{"reportes":[]}"""; runBlocking { api().misReportes() }
        capturarPeticion()
        assertEquals("GET", ultimaPeticion!!.first)
        assertTrue(ultimaPeticion!!.second.startsWith("/mis_reportes?sessionId=station-1234"))
    }

    @Test fun f_post_envia_json_recortado_y_sin_token_en_la_url() {
        respuestaCuerpo = """{"reporte":$reporteJson}"""
        runBlocking { api().reportar("  Título  ", "  Descripción  ", "Otro", "Media") }
        capturarPeticion()
        val (metodo, ruta, cuerpo) = ultimaPeticion!!
        assertEquals("POST", metodo)
        assertEquals("/reportar", ruta)                       // la URL no contiene el token
        assertTrue(cuerpo.contains("\"titulo\":\"Título\""))  // trim() aplicado
        assertTrue(cuerpo.contains("\"token\":\"token-secreto\""))
    }
}
