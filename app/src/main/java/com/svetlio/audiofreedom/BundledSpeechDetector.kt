package com.svetlio.audiofreedom

import android.content.Context
import java.io.File
import java.security.MessageDigest

internal object BundledSpeechDetector {
    private const val Asset = "ggml-silero-v6.2.0.bin"
    private const val Digest = "2aa269b785eeb53a82983a20501ddf7c1d9c48e33ab63a41391ac6c9f7fb6987"
    private var verifiedPath: String? = null

    @Synchronized
    fun path(context: Context): String {
        val file = File(context.noBackupFilesDir, Asset)
        if (verifiedPath == file.absolutePath && file.length() == 885098L) return file.absolutePath
        if (!file.isFile || digest(file) != Digest) {
            val temporary = File(context.noBackupFilesDir, "$Asset.tmp")
            try {
                context.assets.open(Asset).use { input -> temporary.outputStream().use(input::copyTo) }
                check(digest(temporary) == Digest) { "Bundled speech detector failed verification" }
                check(temporary.renameTo(file)) { "Could not install bundled speech detector" }
            } finally {
                temporary.delete()
            }
        }
        verifiedPath = file.absolutePath
        return file.absolutePath
    }

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count >= 0) {
                if (count > 0) digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
