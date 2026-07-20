package com.hartmann.pixeldream.feedback

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Reads a content:// Uri (e.g. from the system photo picker) and Base64-encodes
 * its bytes for upload to the GitHub Contents API. Uses [Base64.NO_WRAP] because
 * GitHub rejects line breaks in the JSON `content` field.
 */
object ImageEncoder {

    @Throws(IOException::class)
    fun uriToBase64(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val outputStream = ByteArrayOutputStream()
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                outputStream.write(buffer, 0, read)
            }
        } ?: throw IOException("Unable to open input stream for $uri")
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Builds a path-safe, unique filename inside the feedback assets directory.
     * Example: feedback-assets/issue-20260719-181230-7c3f.png
     */
    fun uniqueAssetName(extension: String = "png"): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val suffix = buildString(4) {
            val random = java.util.Random()
            repeat(4) {
                append("abcdefghijklmnopqrstuvwxyz0123456789"[random.nextInt(36)])
            }
        }
        val ext = extension.trimStart('.').lowercase()
        return "issue-$stamp-$suffix.$ext"
    }
}
