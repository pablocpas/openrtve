package es.openrtve.data

import androidx.core.util.AtomicFile
import java.io.File
import java.security.MessageDigest

data class CachedDocument(
    val raw: String,
    val savedAtMillis: Long,
    /** Validador del servidor para revalidar con `If-None-Match`. */
    val etag: String?,
)

class RawDocumentCache(private val directory: File) {
    fun read(key: String): CachedDocument? = runCatching {
        val file = fileFor(key)
        if (!file.isFile) return null
        val raw = AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        val etag = etagFileFor(key).takeIf { it.isFile }?.readText(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
        CachedDocument(raw = raw, savedAtMillis = file.lastModified(), etag = etag)
    }.getOrNull()

    fun write(key: String, raw: String, savedAtMillis: Long, etag: String? = null) {
        directory.mkdirs()
        val atomicFile = AtomicFile(fileFor(key))
        val output = atomicFile.startWrite()
        try {
            output.write(raw.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
            atomicFile.baseFile.setLastModified(savedAtMillis)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
        val etagFile = etagFileFor(key)
        if (etag != null) etagFile.writeText(etag, Charsets.UTF_8) else etagFile.delete()
    }

    /** Un 304 confirma que la copia sigue vigente: solo se renueva su fecha. */
    fun touch(key: String, savedAtMillis: Long) {
        fileFor(key).setLastModified(savedAtMillis)
    }

    fun sizeBytes(): Long = directory.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(key: String): File = File(directory, "${digest(key)}.json")

    private fun etagFileFor(key: String): File = File(directory, "${digest(key)}.etag")

    private fun digest(key: String): String = MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { "%02x".format(it) }
}
