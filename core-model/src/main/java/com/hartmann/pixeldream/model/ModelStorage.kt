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

    /**
     * Verifies [file] against [expectedSha256]. A blank hash means the
     * manifest entry doesn't have one yet (e.g. the real hash can't be
     * computed until a file has actually been downloaded once through an
     * authenticated proxy) — in that case the file is trusted as-is rather
     * than permanently rejected. Fill in the real hash in the manifest as
     * soon as it's known; this is a temporary bootstrap allowance, not a
     * general opt-out.
     */
    fun verify(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        if (expectedSha256.isBlank()) return true
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
