package com.synthesis.service

import com.synthesis.domain.model.exoplanet.FramePhotometrySample
import java.io.File
import java.util.UUID

/** Append-only FITS session journal. No automatic deletion or synthetic samples. */
class PhotometryHistory(private val root: File, private val maxBytes: Long = 32L * 1024 * 1024, private val evidence: FitsEvidenceStore? = null) {
    companion object { private val journalLock = Any() }
    @Synchronized fun start(): String {
        check(root.isDirectory || root.mkdirs())
        val id = UUID.randomUUID().toString()
        check(file(id).createNewFile())
        return id
    }
    private fun file(id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(root, "$id.v2.csv")
    }
    @Synchronized fun append(id: String, s: FramePhotometrySample, fitsHash: String) = synchronized(journalLock) {
        val f = file(id)
        check(f.isFile)
        checkNotNull(evidence).readVerified(fitsHash)
        val previous = scan(id, false, false, retainRows = false).rows.lastOrNull()
        require(previous == null || s.bjd > previous.split(",")[3].toDouble())
        val values = listOf(s.timeMinutes,s.bjd,s.targetFlux,s.comparisonFlux,s.relativeFlux,s.normalizedFlux,s.fluxError,s.targetX.toDouble(),s.targetY.toDouble(),s.driftXPix.toDouble(),s.driftYPix.toDouble(),s.seeingFwhmPix.toDouble(),s.skyBackgroundAdu)
        require(values.all { it.isFinite() } && s.bjd > 0)
        val body = listOf(s.frameIndex,s.timestampMs).joinToString(",") + "," + values.joinToString(",") + "," + fitsHash + "," + (previous?.substringAfterLast(',') ?: "GENESIS")
        val bytes = (body + "," + FitsEvidenceStore.sha256(body.toByteArray(Charsets.UTF_8)) + "\n").toByteArray(Charsets.UTF_8)
        check((root.listFiles() ?: error("History unavailable")).sumOf { it.length() } + bytes.size <= maxBytes) { "Photometry archive full" }
        f.outputStreamAppend(bytes)
    }
    data class Recovery(val rows: List<String>, val incompleteTail: Boolean)
    @Synchronized fun verify(id: String): List<String> = scan(id, false, true).rows
    /** Read-only recovery: only an unterminated final record may be excluded. */
    @Synchronized fun recoverPrefix(id: String): Recovery = scan(id, true, true)
    private fun scan(id: String, recover: Boolean, checkEvidence: Boolean, retainRows: Boolean = true): Recovery {
        val f = file(id)
        require(f.isFile && f.length() <= maxBytes)
        val incomplete = java.io.RandomAccessFile(f, "r").use { input ->
            if (input.length() == 0L) false else {
                input.seek(input.length() - 1)
                input.read() != 10
            }
        }
        check(!incomplete || recover) { "Incomplete journal; original preserved" }
        var previous = "GENESIS"
        var lastBjd = Double.NEGATIVE_INFINITY
        val checked = mutableSetOf<String>()
        val rows = mutableListOf<String>()
        f.bufferedReader(Charsets.UTF_8).use { reader ->
            var pending = reader.readLfLine()
            while (pending != null) {
                val line = pending
                pending = reader.readLfLine()
                if (pending == null && incomplete) break
                val fields = line.split(',')
                require(fields.size == 18 && fields[16] == previous)
                check(FitsEvidenceStore.sha256(line.substringBeforeLast(',').toByteArray(Charsets.UTF_8)) == fields[17])
                fields[0].toLong(); fields[1].toLong()
                require(fields.subList(2,15).all { it.toDouble().isFinite() })
                val bjd = fields[3].toDouble()
                require(bjd > 0 && bjd > lastBjd)
                require(fields[15].matches(Regex("[0-9a-f]{64}")))
                if (checkEvidence && checked.add(fields[15])) checkNotNull(evidence).readVerified(fields[15])
                lastBjd = bjd
                previous = fields[17]
                if (!retainRows) rows.clear()
                rows.add(line)
            }
        }
        return Recovery(rows, incomplete)
    }
    private fun java.io.BufferedReader.readLfLine(): String? {
        val line = StringBuilder()
        while (true) {
            val c = read()
            if (c == -1) return if (line.isEmpty()) null else line.toString()
            if (c == 10) return line.toString()
            line.append(c.toChar())
        }
    }
    private fun File.outputStreamAppend(bytes: ByteArray) {
        java.io.FileOutputStream(this,true).use { it.write(bytes); it.fd.sync() }
    }
    @Synchronized fun sessions(): List<String> = root.listFiles()?.filter { it.isFile && it.name.endsWith(".v2.csv") }?.map { it.name.removeSuffix(".v2.csv") }?.sorted() ?: emptyList()
}
