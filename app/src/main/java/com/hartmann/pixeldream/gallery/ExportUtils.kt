package com.hartmann.pixeldream.gallery

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Copies a generated image into the system Photos gallery via MediaStore —
 * no storage permission needed on API 29+ for media the app itself created.
 */
fun exportToGallery(context: Context, imageFile: File): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, imageFile.name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixelDream")
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return runCatching {
        resolver.openOutputStream(uri)?.use { out ->
            imageFile.inputStream().use { it.copyTo(out) }
        }
    }.isSuccess
}

/** Builds a share intent for a generated image via FileProvider, for the system share sheet. */
fun shareImageIntent(context: Context, imageFile: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    return Intent.createChooser(sendIntent, "Share image").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
