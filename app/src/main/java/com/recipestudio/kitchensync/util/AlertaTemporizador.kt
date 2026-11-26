package com.recipestudio.kitchensync.util

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Sonido de alarma y vibración cuando termina un temporizador de cocción (para que no se queme nada). */
object AlertaTemporizador {
    private val PATRON = longArrayOf(0, 600, 300, 600, 300, 900)

    fun sonar(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context.applicationContext, uri)?.play()
        } catch (e: Exception) {
            // sin sonido disponible: la vibración y el aviso en pantalla siguen funcionando
        }
        vibrar(context)
    }

    @Suppress("DEPRECATION")
    private fun vibrar(context: Context) {
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (v == null || !v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createWaveform(PATRON, -1))
            else v.vibrate(PATRON, -1)
        } catch (e: Exception) {
            // algunos dispositivos restringen la vibración: no es crítico
        }
    }
}
