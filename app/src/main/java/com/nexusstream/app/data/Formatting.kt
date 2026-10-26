package com.nexusstream.app.data

import java.util.Locale

/** "512 KB", "34.5 MB", "1.20 GB" for a size in bytes. */
fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes < 0 -> ""
        bytes < kb -> "$bytes B"
        bytes < mb -> String.format(Locale.US, "%.0f KB", bytes / kb)
        bytes < gb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        else -> String.format(Locale.US, "%.2f GB", bytes / gb)
    }
}
