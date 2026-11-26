package com.recipestudio.kitchensync.security

import com.recipestudio.kitchensync.security.InputValidator
import com.recipestudio.kitchensync.security.Validacion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Práctica 11 - Fase 2. MÉTODO DE VALIDACIÓN: InputValidator.validarReporte(titulo, descripcion)
 *
 * Decisiones (when con 4 ramas):
 *   D1: titulo.isBlank() || descripcion.isBlank()          -> condiciones C1 y C2
 *   D2: titulo.trim().length !in 3..80
 *   D3: descripcion.trim().length !in 5..500
 *   else -> Ok
 * Complejidad ciclomática V(G) = predicados (C1, C2, D2, D3 = 4) + 1 = 5 caminos independientes:
 *   P1a: C1 verdadera   P1b: C1 falsa y C2 verdadera   P2: D1 falsa, D2 verdadera
 *   P3: D1 y D2 falsas, D3 verdadera   P4: todas falsas (Ok)
 */
class ValidarReporteWhiteBoxTest {
    private val msgVacio = Validacion.Error("Completa el título y la descripción")
    private val msgTitulo = Validacion.Error("El título debe tener entre 3 y 80 caracteres")
    private val msgDesc = Validacion.Error("La descripción debe tener entre 5 y 500 caracteres")

    // ----- cobertura de CONDICIONES (cada condición de D1 en verdadero y falso) -----
    @Test fun c1_verdadera_c2_falsa() = assertEquals(msgVacio, InputValidator.validarReporte("   ", "Descripción válida"))
    @Test fun c1_falsa_c2_verdadera() = assertEquals(msgVacio, InputValidator.validarReporte("Título válido", "   "))
    @Test fun c1_verdadera_c2_verdadera() = assertEquals(msgVacio, InputValidator.validarReporte("", ""))
    @Test fun c1_falsa_c2_falsa_continua_a_D2() = assertTrue(InputValidator.validarReporte("Título válido", "Descripción válida").esValida)

    // ----- cobertura de DECISIONES / CAMINOS -----
    @Test fun p2_titulo_corto() = assertEquals(msgTitulo, InputValidator.validarReporte("ab", "Descripción válida"))
    @Test fun p3_descripcion_corta() = assertEquals(msgDesc, InputValidator.validarReporte("Título válido", "abcd"))
    @Test fun p4_todo_valido() = assertEquals(Validacion.Ok, InputValidator.validarReporte("Título válido", "Descripción válida"))

    // ----- VALORES LÍMITE del título (3..80) -----
    @Test fun titulo_2_caracteres_error() = assertEquals(msgTitulo, InputValidator.validarReporte("a".repeat(2), "Descripción válida"))
    @Test fun titulo_3_caracteres_ok() = assertTrue(InputValidator.validarReporte("a".repeat(3), "Descripción válida").esValida)
    @Test fun titulo_80_caracteres_ok() = assertTrue(InputValidator.validarReporte("a".repeat(80), "Descripción válida").esValida)
    @Test fun titulo_81_caracteres_error() = assertEquals(msgTitulo, InputValidator.validarReporte("a".repeat(81), "Descripción válida"))

    // ----- VALORES LÍMITE de la descripción (5..500) -----
    @Test fun descripcion_4_caracteres_error() = assertEquals(msgDesc, InputValidator.validarReporte("Título", "a".repeat(4)))
    @Test fun descripcion_5_caracteres_ok() = assertTrue(InputValidator.validarReporte("Título", "a".repeat(5)).esValida)
    @Test fun descripcion_500_caracteres_ok() = assertTrue(InputValidator.validarReporte("Título", "a".repeat(500)).esValida)
    @Test fun descripcion_501_caracteres_error() = assertEquals(msgDesc, InputValidator.validarReporte("Título", "a".repeat(501)))

    // ----- el trim() cuenta: los espacios de los extremos no suman longitud -----
    @Test fun espacios_extremos_no_cuentan_en_el_titulo() = assertEquals(msgTitulo, InputValidator.validarReporte("  ab  ", "Descripción válida"))
    @Test fun espacios_extremos_no_cuentan_en_la_descripcion() = assertEquals(msgDesc, InputValidator.validarReporte("Título", "  abcd  "))

    // ----- orden de evaluación: con título Y descripción inválidos gana el mensaje del título -----
    @Test fun prioridad_de_mensajes() = assertEquals(msgTitulo, InputValidator.validarReporte("ab", "abc"))
}
