package com.ai.trackex.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object TempImageManager {

    private const val CACHE_DIR_NAME = "bill_images"
    private const val STALE_THRESHOLD_MS = 30 * 60 * 1000L // 30 minutes

    fun createTempImageUri(context: Context): Uri {
        val imageDir = File(context.cacheDir, CACHE_DIR_NAME)
        imageDir.mkdirs()
        val tempFile = File.createTempFile("bill_", ".jpg", imageDir)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    /**
     * Delete a specific camera-captured temp file by its URI.
     * Gallery-picked images (content:// URIs not in our cache) are left untouched.
     */
    fun deleteTempFile(context: Context, uri: String) {
        if (!isCameraImage(uri)) return
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) return
            cacheDir.listFiles()?.forEach { file ->
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                if (fileUri.toString() == uri) {
                    file.delete()
                }
            }
        } catch (_: Exception) { }
    }

    /**
     * Clean up stale temp files older than [STALE_THRESHOLD_MS].
     * Call this on app/screen launch to handle files left behind by
     * crashes or abandoned sessions.
     */
    fun cleanupStaleFiles(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) return
            val cutoff = System.currentTimeMillis() - STALE_THRESHOLD_MS
            cacheDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        } catch (_: Exception) { }
    }

    /**
     * Returns true if the URI points to a camera-captured temp file
     * in our cache directory (as opposed to a gallery content:// URI).
     */
    fun isCameraImage(uri: String): Boolean {
        return uri.contains(CACHE_DIR_NAME)
    }
}
