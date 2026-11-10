package com.recipestudio.kitchensync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recipestudio.kitchensync.data.session.InsumoSubstitutionManager
import com.recipestudio.kitchensync.domain.NivelImpacto
import com.recipestudio.kitchensync.domain.SubstitutionCalculator
import com.recipestudio.kitchensync.security.InputValidator
import com.recipestudio.kitchensync.security.Validacion

/** "No tengo azúcar blanca": muestra con qué reemplazarla, cuánto usar y cuánto cambia el sabor. */
@Composable
fun SustitucionesScreen() {
    var modo by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Paleta.Crema)) {
        TabRow(selectedTabIndex = modo, containerColor = Paleta.Crema) {
            Tab(selected = modo == 0, onClick = { modo = 0 }, text = { Text("📷 Con foto (IA)") })
            Tab(selected = modo == 1, onClick = { modo = 1 }, text = { Text("Sugerencias") })
        }
        Box(Modifier.weight(1f)) {
            if (modo == 0) SustitucionFotoPanel() else SugerenciasPanel()
        }
    }
}

@Composable
private fun SugerenciasPanel() {
    val originales = remember { InsumoSubstitutionManager.listarOriginales() }
    var elegido by remember { mutableStateOf(originales.firstOrNull { it == "Azúcar Blanca" } ?: originales.first()) }
    var cantidad by remember { mutableStateOf("100") }
    val validacion = InputValidator.validarCantidad(cantidad)
    val analisis = if (validacion.esValida) {
        SubstitutionCalculator.analizar(elegido, cantidad.trim().replace(',', '.').toDouble())
    } else null

    Column(
        Modifier.fillMaxSize().background(Paleta.Crema).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Sustituir ingrediente", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Paleta.Texto)
        Text("¿Qué ingrediente te falta?", color = Paleta.Gris, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            originales.forEach { nombre ->
                FilterChip(selected = nombre == elegido, onClick = { elegido = nombre }, label = { Text(nombre) })
            }
        }
        OutlinedTextField(
            value = cantidad,
            onValueChange = { cantidad = it.take(8) },
            label = { Text("Cantidad que pide la receta (g, ml o piezas)") },
            singleLine = true,
            isError = validacion is Validacion.Error,
            supportingText = { (validacion as? Validacion.Error)?.let { Text(it.mensaje) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        if (analisis != null) {
            val color = when (analisis.nivel) {
                NivelImpacto.BAJO -> Paleta.Verde
                NivelImpacto.MODERADO -> Paleta.Amarillo
                NivelImpacto.ALTO -> Paleta.Naranja
            }
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Paleta.Tarjeta)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Usa ${analisis.sustituto}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Paleta.Texto)
                    Text("Cantidad: ${analisis.cantidadAjustada} (en lugar de ${analisis.cantidadOriginal})", color = Paleta.Texto)
                    Text("Cambio de sabor: ${analisis.impactoSaborPct}%", color = Paleta.Texto)
                    LinearProgressIndicator(progress = { analisis.impactoSaborPct / 100f }, color = color, modifier = Modifier.fillMaxWidth())
                    Text("Cambio de textura: ${analisis.impactoTexturaPct}%", color = Paleta.Texto)
                    LinearProgressIndicator(progress = { analisis.impactoTexturaPct / 100f }, color = color, modifier = Modifier.fillMaxWidth())
                    Text("Impacto general: ${analisis.nivel.etiqueta}", color = color, fontWeight = FontWeight.Bold)
                    Text(analisis.nota, color = Paleta.Gris, fontSize = 13.sp)
                }
            }
        }
    }
}
