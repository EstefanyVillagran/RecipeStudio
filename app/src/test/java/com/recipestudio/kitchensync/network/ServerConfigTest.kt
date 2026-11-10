package com.recipestudio.kitchensync.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerConfigTest {
    @Test
    fun `normalizar rechaza hosts locales y privadas`() {
        assertEquals(ServerConfig.DEFAULT, ServerConfig.normalizar("http://10.24.33.200:10000"))
        assertEquals(ServerConfig.DEFAULT, ServerConfig.normalizar("http://192.168.1.10:8080"))
        assertEquals(ServerConfig.DEFAULT, ServerConfig.normalizar("http://127.0.0.1:8080"))
        assertEquals(ServerConfig.DEFAULT, ServerConfig.normalizar("http://localhost:8080"))
        assertEquals("https://recipestudio.onrender.com", ServerConfig.normalizar("https://recipestudio.onrender.com"))
    }

    @Test
    fun `normalizar no permite un valor viejo guardado en local`() {
        val viejo = "http://10.24.33.200:10000"
        assertEquals(ServerConfig.DEFAULT, ServerConfig.normalizar(viejo))
    }
}
