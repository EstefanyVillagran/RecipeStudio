package com.recipestudio.kitchensync.shared.hardware

expect class QrScanner {
    fun iniciarEscaneoInsumo(onInsumoDetectado: (codigoQR: String) -> Unit)
}

expect class BiometricAuth {
    fun autenticarChef(
        onExito: () -> Unit,
        onError: (mensaje: String) -> Unit
    )
}
