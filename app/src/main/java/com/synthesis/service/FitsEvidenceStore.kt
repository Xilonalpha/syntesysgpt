package com.synthesis.service

import java.io.File
import java.security.MessageDigest

/** Private, content-addressed archive. Hash checks detect changes, not scientific validity. */
class FitsEvidenceStore(private val root: File, private val maxBytes: Long = 512L * 1024 * 1024) {
    companion object {
        private val writeLock = Any()
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun save(bytes: ByteArray): String = synchronized(writeLock) {
        require(bytes.isNotEmpty() && bytes.size <= 32 * 1024 * 1024)
        val hash = sha256(bytes)
        check(root.isDirectory || root.mkdirs())
        val target = File(root, "$hash.fits")
        if (!target.exists()) {
            val used = checkNotNull(root.listFiles()).filter { it.isFile }.sumOf { it.length() }
            check(maxBytes > 0 && used <= maxBytes - bytes.size) { "Limita arhivei FITS depasita" }
            val temp = File.createTempFile("fits-", ".tmp", root)
            try {
                temp.outputStream().use { it.write(bytes); it.fd.sync() }
                check(temp.renameTo(target)) { "Nu se poate salva FITS" }
            } finally { temp.delete() }
        }
        readVerified(hash)
        hash
    }
    fun readVerified(hash: String): ByteArray {
        require(hash.matches(Regex("[0-9a-f]{64}")))
        val file = File(root, "$hash.fits")
        require(file.isFile && file.length() in 1..(32L * 1024 * 1024))
        val bytes = file.readBytes()
        check(sha256(bytes) == hash) { "Integritate FITS compromisă" }
        return bytes
    }
}
