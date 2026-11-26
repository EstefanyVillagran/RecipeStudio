package com.recipestudio.kitchensync.whitebox

import com.recipestudio.kitchensync.network.ApiException
import com.recipestudio.kitchensync.network.KitchenApi
import com.recipestudio.kitchensync.network.SesionQr
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Práctica 11 - Fase 2. MÉTODO DE COMUNICACIÓN: KitchenApi.call() (probado a través de misReportes() y reportar()).
 * Se levanta un servidor HTTP real en localhost (JDK) que devuelve el código que se quiera: así se recorren TODAS las
 * ramas de call() sin depender de internet ni de Render.
 *
 * Caminos de call():
 *   A: codigo 2xx + cuerpo JSON                  B: 2xx + cuerpo vacío/ilegible -> JSONObject() vacío
 *   C: no 2xx con JSON {"error": ...}            D: no 2xx sin JSON -> mensajePorCodigo (401, 403, otro)
 *   E: IOException (sin conexión)                F: con cuerpo (POST) vs sin cuerpo (GET)
 */
class KitchenApiWhiteBoxTest {
    private lateinit var server: HttpServer
    private var respuestaCodigo = 200
    private var respuestaCuerpo = ""
    private var ultimaPeticion: Triple<String, String, String>? = null   // metodo, ruta, cuerpo
    private val sesion = SesionQr("station-1234", "token-secreto", "Pixel 7")

    private fun api() = KitchenApi("http://127.0.0.1:${server.address.port}", sesion)

    @Before fun arrancar() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { ex ->
            val cuerpoPeticion = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            ultimaPeticion = Triple(ex.requestMethod, ex.requestURI.toString(), cuerpoPeticion)
            val bytes = respuestaCuerpo.toByteArray()
            ex.sendResponseHeaders(respuestaCodigo, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
        }
        server.start()
    }

    @After fun detener() = server.stop(0)

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
        assertEquals("GET", ultimaPeticion!!.first)
        assertTrue(ultimaPeticion!!.second.startsWith("/mis_reportes?sessionId=station-1234"))
    }

    @Test fun f_post_envia_json_recortado_y_sin_token_en_la_url() {
        respuestaCuerpo = """{"reporte":$reporteJson}"""
        runBlocking { api().reportar("  Título  ", "  Descripción  ", "Otro", "Media") }
        val (metodo, ruta, cuerpo) = ultimaPeticion!!
        assertEquals("POST", metodo)
        assertEquals("/reportar", ruta)                       // la URL no contiene el token
        assertTrue(cuerpo.contains("\"titulo\":\"Título\""))  // trim() aplicado
        assertTrue(cuerpo.contains("\"token\":\"token-secreto\""))
    }
}
