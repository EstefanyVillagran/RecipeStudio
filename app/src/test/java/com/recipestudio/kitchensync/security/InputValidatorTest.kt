package com.recipestudio.kitchensync.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorTest {
    @Test fun `usuario con solo espacios se rechaza`() = assertFalse(InputValidator.validarUsuario("     ").esValida)
    @Test fun `usuario valido se acepta`() = assertTrue(InputValidator.validarUsuario("cocinero1").esValida)
    @Test fun `usuario demasiado largo se rechaza`() = assertFalse(InputValidator.validarUsuario("a".repeat(31)).esValida)
    @Test fun `usuario con simbolos peligrosos se rechaza`() = assertFalse(InputValidator.validarUsuario("admin'; --").esValida)

    @Test fun `password de siete caracteres se rechaza`() = assertFalse(InputValidator.validarPassword("1234567").esValida)
    @Test fun `password de ocho caracteres se acepta`() = assertTrue(InputValidator.validarPassword("12345678").esValida)
    @Test fun `password vacia se rechaza`() = assertFalse(InputValidator.validarPassword("").esValida)

    @Test fun `mensaje vacio o con espacios se rechaza`() {
        assertFalse(InputValidator.validarMensaje("").esValida)
        assertFalse(InputValidator.validarMensaje("   ").esValida)
    }

    @Test fun `mensaje de 280 se acepta y de 281 no`() {
        assertTrue(InputValidator.validarMensaje("a".repeat(280)).esValida)
        assertFalse(InputValidator.validarMensaje("a".repeat(281)).esValida)
    }

    @Test fun `reporte sin campos se rechaza`() = assertFalse(InputValidator.validarReporte("", "").esValida)
    @Test fun `reporte valido se acepta`() = assertTrue(InputValidator.validarReporte("Receta no disponible", "No carga").esValida)

    @Test fun `cantidad invalida`() {
        assertFalse(InputValidator.validarCantidad("abc").esValida)
        assertFalse(InputValidator.validarCantidad("0").esValida)
        assertFalse(InputValidator.validarCantidad("-5").esValida)
        assertTrue(InputValidator.validarCantidad("2,5").esValida)
    }

    @Test fun `url de servidor`() {
        assertTrue(InputValidator.validarUrlServidor("http://192.168.1.10:8080").esValida)
        assertFalse(InputValidator.validarUrlServidor("192.168.1.10").esValida)
        assertFalse(InputValidator.validarUrlServidor("http://host con espacios").esValida)
    }

    @Test fun `mensaje de error es generico`() {
        val e = InputValidator.validarPassword("abc") as Validacion.Error
        assertEquals("La contraseña debe tener al menos 8 caracteres", e.mensaje)
    }
}
