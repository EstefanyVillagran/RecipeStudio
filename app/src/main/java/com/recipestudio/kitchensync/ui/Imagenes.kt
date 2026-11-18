package com.recipestudio.kitchensync.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Utilidades de imagen compartidas por las pantallas (foto final, sustitución con IA y muro). */

internal fun reducir(b: Bitmap, max: Int): Bitmap {
    val lado = maxOf(b.width, b.height)
    if (lado <= max) return b
    val f = max.toFloat() / lado
    return Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true)
}

/** JPEG con el lado mayor limitado a [max] px. */
internal fun aJpeg(b: Bitmap, max: Int = 1024, calidad: Int = 85): ByteArray =
    ByteArrayOutputStream().also { reducir(b, max).compress(Bitmap.CompressFormat.JPEG, calidad, it) }.toByteArray()

/** Lee una imagen sin cargarla completa en memoria y la reduce a [max] px. */
internal fun leerReducida(context: Context, uri: Uri, max: Int = 1024): Bitmap? = try {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
    var muestreo = 1
    while (maxOf(limites.outWidth, limites.outHeight) / muestreo > max * 2) muestreo *= 2
    val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }?.let { reducir(it, max) }
} catch (e: Exception) {
    null
}

internal fun bitmapDeBytes(bytes: ByteArray?): Bitmap? =
    if (bytes == null || bytes.isEmpty()) null else try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (e: Exception) { null }

internal fun bitmapDeBase64(texto: String?): Bitmap? =
    if (texto.isNullOrBlank()) null else try { bitmapDeBytes(Base64.decode(texto, Base64.NO_WRAP)) } catch (e: IllegalArgumentException) { null }

/** Descarga una imagen (p. ej. la URL prefirmada de S3). Debe llamarse fuera del hilo principal. */
internal fun bitmapDeUrl(url: String): Bitmap? = try {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        if (conn.responseCode in 200..299) conn.inputStream.use { BitmapFactory.decodeStream(it) } else null
    } finally {
        conn.disconnect()
    }
} catch (e: Exception) {
    null
}
