package com.synthesis.service

import java.io.File
import java.util.UUID

/** Immutable local experiment input record. Not a scientific certification. */
class ExperimentManifestStore(private val root: File, private val evidence: FitsEvidenceStore) {
    fun save(tic: Long, sources: List<Source>, algorithm: String): String {
        require(tic > 0 && sources.isNotEmpty() && sources.size <= 20)
        require(algorithm.matches(Regex("[A-Za-z0-9_.-]+")))
        require(sources.map { it.sector }.distinct().size == sources.size)
        for (s in sources) {
            require(s.sector > 0 && s.points >= 8)
            require(s.uri.matches(Regex("mast:TESS/product/[A-Za-z0-9_-]+_lc\\.fits")))
            evidence.readVerified(s.sha256)
        }
        check(root.isDirectory || root.mkdirs())
        val id = UUID.randomUUID().toString()
        val temp = File.createTempFile("experiment-", ".tmp", root)
        try {
            val body = buildString {
                append("schema=2\ntic=$tic\nalgorithm=$algorithm\ncreated_ms=${System.currentTimeMillis()}\n")
                for (s in sources.sortedBy { it.sector }) append("source=${s.sector}\t${s.points}\t${s.sha256}\t${s.uri}\n")
            }
            val payload = body + "sha256=${FitsEvidenceStore.sha256(body.toByteArray(Charsets.UTF_8))}\n"
            temp.outputStream().use { it.write(payload.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            check(temp.renameTo(File(root, "$id.manifest")))
        } finally { temp.delete() }
        return id
    }
    data class ArchiveEntry(val id: String, val manifest: Manifest)
    data class ArchivePage(val entries: List<ArchiveEntry>, val rejected: List<String>, val nextOffset: Int?)

    /** Bounded verification of local manifests; corrupt evidence is never listed as valid. */
    fun listVerified(offset: Int = 0, limit: Int = 10): ArchivePage {
        require(offset >= 0 && limit in 1..50)
        if (!root.exists()) return ArchivePage(emptyList(), emptyList(), null)
        val files = checkNotNull(root.listFiles()).filter { it.name.endsWith(".manifest") }
            .sortedBy { it.name }
        val entries = mutableListOf<ArchiveEntry>()
        val rejected = mutableListOf<String>()
        val page = files.drop(offset).take(limit)
        for (file in page) {
            val id = file.name.removeSuffix(".manifest")
            try {
                entries.add(ArchiveEntry(id, readVerified(id)))
            } catch (e: Exception) {
                rejected.add(file.name)
            }
        }
        val next = offset.toLong() + page.size
        return ArchivePage(entries, rejected, if (next < files.size) next.toInt() else null)
    }

    data class Manifest(val tic: Long, val algorithm: String, val createdMs: Long, val sources: List<Source>)
    fun readVerified(id: String): Manifest {
        require(UUID.fromString(id).toString() == id)
        val file = File(root, "$id.manifest")
        require(file.isFile && file.length() in 1..65536L)
        val text = file.readText(Charsets.UTF_8)
        val lines = text.removeSuffix("\n").split("\n")
        require(lines.size in 6..25 && lines[0] == "schema=2")
        val body = lines.dropLast(1).joinToString("\n", postfix = "\n")
        check(lines.last() == "sha256=${FitsEvidenceStore.sha256(body.toByteArray(Charsets.UTF_8))}")
        fun field(index: Int, prefix: String): String {
            require(lines[index].startsWith(prefix))
            return lines[index].removePrefix(prefix)
        }
        val tic = field(1, "tic=").toLong()
        val algorithm = field(2, "algorithm=")
        val created = field(3, "created_ms=").toLong()
        require(tic > 0 && created > 0 && algorithm.matches(Regex("[A-Za-z0-9_.-]+")))
        val sources = (4 until lines.lastIndex).map { i ->
            val parts = field(i, "source=").split("\t")
            require(parts.size == 4)
            val source = Source(parts[3], parts[2], parts[0].toInt(), parts[1].toInt())
            require(source.sector > 0 && source.points >= 8)
            require(source.uri.matches(Regex("mast:TESS/product/[A-Za-z0-9_-]+_lc\\.fits")))
            evidence.readVerified(source.sha256)
            source
        }
        require(sources.map { it.sector }.distinct().size == sources.size)
        return Manifest(tic, algorithm, created, sources)
    }
    data class Source(val uri: String, val sha256: String, val sector: Int, val points: Int)
}
