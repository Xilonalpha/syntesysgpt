package com.synthesis.service

import java.io.File

/** Integrity of retrieved bytes, not proof of origin or scientific validity. */
class RawResponseArchive(private val root: File, private val quotaBytes: Long = 128L * 1024 * 1024) {
    companion object {
        private val lock = Any()
        private const val MAX_RESPONSE = 8 * 1024 * 1024
    }
    fun save(bytes: ByteArray): String = synchronized(lock) {
        require(bytes.isNotEmpty() && bytes.size <= MAX_RESPONSE)
        val hash = FitsEvidenceStore.sha256(bytes)
        check(root.isDirectory || root.mkdirs())
        val target = File(root, "$hash.raw")
        if (!target.exists()) {
            val used = checkNotNull(root.listFiles()).filter { it.isFile }.sumOf { it.length() }
            check(quotaBytes >= bytes.size && used <= quotaBytes - bytes.size) { "Raw archive quota exceeded" }
            val temp = File.createTempFile("response-", ".tmp", root)
            try {
                temp.outputStream().use { it.write(bytes); it.fd.sync() }
                check(temp.renameTo(target))
            } finally { temp.delete() }
        }
        readVerified(hash)
        hash
    }
    fun readVerified(hash: String): ByteArray {
        require(hash.matches(Regex("[0-9a-f]{64}")))
        val file = File(root, "$hash.raw")
        require(file.isFile && file.length() in 1..MAX_RESPONSE.toLong())
        val bytes = file.readBytes()
        check(FitsEvidenceStore.sha256(bytes) == hash) { "Raw response integrity failure" }
        return bytes
    }
}
