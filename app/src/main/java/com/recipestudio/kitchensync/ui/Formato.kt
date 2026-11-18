package com.recipestudio.kitchensync.ui

/** "hace 5 min", "hace 2 h", "ayer"... para las listas de reportes. */
internal fun haceTiempo(ms: Long, ahora: Long = System.currentTimeMillis()): String {
    val seg = ((ahora - ms) / 1000).coerceAtLeast(0)
    return when {
        ms <= 0L -> ""
        seg < 60 -> "hace un momento"
        seg < 3600 -> "hace ${seg / 60} min"
        seg < 86_400 -> "hace ${seg / 3600} h"
        seg < 172_800 -> "ayer"
        else -> "hace ${seg / 86_400} días"
    }
}
