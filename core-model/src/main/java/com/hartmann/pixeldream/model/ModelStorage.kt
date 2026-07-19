package com.hartmann.pixeldream.model

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** App-private external storage for downloaded model files — cleared on uninstall. */
class ModelStorage(private val context: Context) {

    fun directoryFor(kind: ModelKind): File {
        val dir = File(context.getExternalFilesDir(null), "models/${kind.name.lowercase()}")
        dir.mkdirs()
        return dir
    }

    fun fileFor(descriptor: ModelDescriptor): File =
        File(directoryFor(descriptor.kind), "${descriptor.version}-${File(descriptor.url).name}")

    fun partialFileFor(descriptor: ModelDescriptor): File =
        File(directoryFor(descriptor.kind), "${fileFor(descriptor).name}.part")

    fun verify(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }
}
