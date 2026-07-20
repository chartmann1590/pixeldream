package com.hartmann.pixeldream.model

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/** App-private external storage for downloaded model files — cleared on uninstall. */
class ModelStorage(private val context: Context) {

    fun directoryFor(kind: ModelKind): File {
        val dir = File(context.getExternalFilesDir(null), "models/${kind.name.lowercase()}")
        dir.mkdirs()
        return dir
    }

    fun fileFor(descriptor: ModelDescriptor): File = File(
        directoryFor(descriptor.kind),
        "${descriptor.version}-${Uri.parse(descriptor.url).lastPathSegment ?: "model.bin"}",
    )

    fun partialFileFor(descriptor: ModelDescriptor): File =
        File(directoryFor(descriptor.kind), "${fileFor(descriptor).name}.part")

    /**
     * Verifies [file] against [expectedSha256]. Fails closed: a blank hash
     * (manifest entry not yet populated with a real one) is treated as
     * unverifiable, not as automatically trusted — the download will
     * correctly never report Ready until the manifest carries a real hash.
     * Do not change this to trust blank hashes; fill in the manifest instead.
     */
    fun verify(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        if (expectedSha256.isBlank()) return false
        val marker = File(file.parentFile, "${file.name}.verified")
        val markerValue = "$expectedSha256:${file.length()}:${file.lastModified()}"
        if (marker.exists() && marker.readText() == markerValue) return true
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
        val verified = actual.equals(expectedSha256, ignoreCase = true)
        if (verified) marker.writeText(markerValue) else marker.delete()
        return verified
    }

    /** Removes obsolete artifacts for the same model kind after a replacement is verified. */
    fun pruneOtherVersions(descriptor: ModelDescriptor) {
        val current = fileFor(descriptor)
        directoryFor(descriptor.kind).listFiles()?.forEach { candidate ->
            if (candidate != current && candidate.name != "${current.name}.verified") {
                candidate.delete()
            }
        }
    }

    fun delete(descriptor: ModelDescriptor) {
        val file = fileFor(descriptor)
        file.delete()
        File(file.parentFile, "${file.name}.verified").delete()
        partialFileFor(descriptor).delete()
    }
}
