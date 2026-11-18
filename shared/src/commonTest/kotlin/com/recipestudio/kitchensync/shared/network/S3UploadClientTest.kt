package com.recipestudio.kitchensync.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Prueba de integración del flujo de 2 pasos con un servidor simulado (MockEngine de Ktor): sin red ni AWS. */
class S3UploadClientTest {
    private val firmada = """{"url":"https://bucket.s3.us-east-1.amazonaws.com/fotos/20261114/aabbccddeeff001122334455.jpg?X-Amz-Signature=abc&X-Amz-Expires=300","key":"fotos/20261114/aabbccddeeff001122334455.jpg","method":"PUT"}"""
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun cliente(
        peticiones: MutableList<HttpRequestData>,
        estadoServidor: HttpStatusCode = HttpStatusCode.OK,
        estadoS3: HttpStatusCode = HttpStatusCode.OK,
        cuerpoServidor: String = firmada
    ) = S3UploadClient("https://srv.onrender.com/", "station-1", "tok", HttpClient(MockEngine { req ->
        peticiones.add(req)
        if (req.method == HttpMethod.Put) respond("", estadoS3) else respond(cuerpoServidor, estadoServidor, json)
    }))

    @Test fun flujo_completo_pide_la_url_y_sube_directo_a_s3() = runTest {
        val reqs = mutableListOf<HttpRequestData>()
        val clave = cliente(reqs).subirFoto(ByteArray(2048) { 7 })
        assertEquals("fotos/20261114/aabbccddeeff001122334455.jpg", clave)
        assertEquals(2, reqs.size)
        assertEquals("srv.onrender.com", reqs[0].url.host)                         // 1.º: al servidor
        assertEquals("/api/upload-url", reqs[0].url.encodedPath)
        assertEquals("station-1", reqs[0].url.parameters["sessionId"])
        assertEquals(HttpMethod.Put, reqs[1].method)                                // 2.º: PUT directo a S3
        assertEquals("bucket.s3.us-east-1.amazonaws.com", reqs[1].url.host)
        assertEquals("abc", reqs[1].url.parameters["X-Amz-Signature"])
        assertEquals("image/jpeg", reqs[1].headers[HttpHeaders.ContentType])
    }

    @Test fun servidor_sin_s3_configurado_marca_noConfigurado() = runTest {
        val c = cliente(mutableListOf(), HttpStatusCode.ServiceUnavailable, cuerpoServidor = """{"error":"S3 no configurado"}""")
        val e = assertFailsWith<S3Exception> { c.subirFoto(ByteArray(10)) }
        assertTrue(e.noConfigurado)
        assertEquals("S3 no configurado", e.message)
    }

    @Test fun sesion_invalida_muestra_el_error_del_servidor() = runTest {
        val c = cliente(mutableListOf(), HttpStatusCode.Forbidden, cuerpoServidor = """{"error":"Sesión inválida"}""")
        val e = assertFailsWith<S3Exception> { c.subirFoto(ByteArray(10)) }
        assertEquals(403, e.codigo)
        assertEquals("Sesión inválida", e.message)
    }

    @Test fun s3_rechaza_la_subida() = runTest {
        val c = cliente(mutableListOf(), estadoS3 = HttpStatusCode.Forbidden)
        val e = assertFailsWith<S3Exception> { c.subirFoto(ByteArray(10)) }
        assertEquals(403, e.codigo)
    }

    @Test fun respuesta_del_servidor_sin_url_es_error() = runTest {
        val c = cliente(mutableListOf(), cuerpoServidor = """{"otra":"cosa"}""")
        assertFailsWith<S3Exception> { c.subirFoto(ByteArray(10)) }
    }

    @Test fun imagen_vacia_o_demasiado_grande_se_rechaza_antes_de_usar_la_red() = runTest {
        val reqs = mutableListOf<HttpRequestData>()
        val c = cliente(reqs)
        assertFailsWith<IllegalArgumentException> { c.subirFoto(ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { c.subirFoto(ByteArray(S3UploadClient.MAX_BYTES + 1)) }
        assertEquals(0, reqs.size)
    }

    @Test fun url_de_descarga() = runTest {
        val c = cliente(mutableListOf(), cuerpoServidor = """{"url":"https://bucket.s3.amazonaws.com/x.jpg?X-Amz-Signature=z","key":"k"}""")
        assertEquals("https://bucket.s3.amazonaws.com/x.jpg?X-Amz-Signature=z", c.urlDescarga("fotos/20261114/aabbccddeeff001122334455.jpg"))
    }

    @Test fun extraer_campo() {
        assertEquals("a/b&c", extraerCampo("""{"x":"a\/b\u0026c"}""", "x"))
        assertEquals("di \"hola\"", extraerCampo("""{"x": "di \"hola\""}""", "x"))
        assertNull(extraerCampo("""{"x":"1"}""", "y"))
    }
}
