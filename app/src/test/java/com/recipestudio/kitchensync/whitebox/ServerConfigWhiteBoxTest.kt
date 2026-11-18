package com.recipestudio.kitchensync.whitebox

import com.recipestudio.kitchensync.network.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Práctica 11 - Fase 2. ServerConfig.normalizar(url) + esHostPrivadoOlocal(host) (privada: se prueba a través de normalizar).
 * Defecto D-01 de la auditoría: el QR llegaba con la IP interna de Render y la app intentaba conectarse a ella.
 * Valores límite de los rangos privados: 172.16.0.0 - 172.31.255.255.
 */
class ServerConfigWhiteBoxTest {
    private val publica = "https://recipestudio.onrender.com"
    private val default = ServerConfig.DEFAULT

    @Test fun nulo_o_vacio_usa_default() { assertEquals(default, ServerConfig.normalizar(null)); assertEquals(default, ServerConfig.normalizar("   ")) }
    @Test fun dominio_publico_se_conserva() = assertEquals(publica, ServerConfig.normalizar("$publica/"))

    // IPs privadas -> se reemplazan por la dirección pública
    @Test fun ip_10() = assertEquals(default, ServerConfig.normalizar("http://10.24.33.202:10000"))
    @Test fun ip_192_168() = assertEquals(default, ServerConfig.normalizar("http://192.168.1.20:8080"))
    @Test fun loopback() = assertEquals(default, ServerConfig.normalizar("http://127.0.0.1:8080"))
    @Test fun localhost() = assertEquals(default, ServerConfig.normalizar("http://localhost:8080"))
    @Test fun link_local_169_254() = assertEquals(default, ServerConfig.normalizar("http://169.254.1.1"))

    // límites del rango 172.16-31
    @Test fun ip_172_15_es_publica() = assertEquals("http://172.15.0.1", ServerConfig.normalizar("http://172.15.0.1"))
    @Test fun ip_172_16_es_privada() = assertEquals(default, ServerConfig.normalizar("http://172.16.0.1"))
    @Test fun ip_172_31_es_privada() = assertEquals(default, ServerConfig.normalizar("http://172.31.255.255"))
    @Test fun ip_172_32_es_publica() = assertEquals("http://172.32.0.1", ServerConfig.normalizar("http://172.32.0.1"))
    @Test fun ip_192_169_es_publica() = assertEquals("http://192.169.0.1", ServerConfig.normalizar("http://192.169.0.1"))

    // octeto fuera de rango no es una IP: se trata como nombre
    @Test fun octeto_300_es_un_nombre_no_una_ip() = assertEquals("http://10.300.1.1", ServerConfig.normalizar("http://10.300.1.1"))
    @Test fun nombre_que_empieza_con_fc_no_es_ipv6() = assertEquals("http://fcbarcelona.com", ServerConfig.normalizar("http://fcbarcelona.com"))
    @Test fun dominio_local_mdns() = assertEquals(default, ServerConfig.normalizar("http://cocina.local:8080"))
}
