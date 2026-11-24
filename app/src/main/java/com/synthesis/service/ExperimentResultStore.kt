package com.synthesis.service

import java.io.File
import java.util.UUID
import com.synthesis.domain.model.exoplanet.PeriodicTransitDetectionResult

/** Stores scalar BLS outputs, not a certification or a complete analysis report. */
class ExperimentResultStore(private val root: File, private val manifests: ExperimentManifestStore, private val maxBytes: Long = 16L * 1024 * 1024) {
    init { require(maxBytes > 0) }
    data class History(val runs: List<String>, val rejected: List<String>)
    /** Lists only verified runs for the requested experiment. Corrupt records are reported. */
    fun history(id: String): History {
        binding(id)
        if (!root.exists()) return History(emptyList(), emptyList())
        val files = checkNotNull(root.listFiles())
        val runs = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        for (file in files.filter { it.name.endsWith(".result") }.sortedBy { it.name }) {
            val run = file.name.removeSuffix(".result")
            try {
                require(file.isFile && file.length() in 1..8192L)
                val lines = file.readLines(Charsets.UTF_8)
                require(lines.size == 8)
                check(lines.last() == FitsEvidenceStore.sha256(lines.dropLast(1).joinToString("\n", postfix="\n").toByteArray(Charsets.UTF_8)))
                if (lines[1] != "experiment=$id") continue
                read(id, run)
                runs.add(run)
            } catch (e: Exception) { rejected.add(file.name) }
        }
        return History(runs, rejected)
    }
    data class Metrics(val detected: Boolean, val values: List<Double?>) {
        init { require(values.size == 6 && values.all { it == null || it.isFinite() }) }
    }
    companion object {
        fun metrics(r: PeriodicTransitDetectionResult) = Metrics(r.isDetected,
            listOf(r.bestPeriodDays, r.epochBjd, r.transitDepthPpm, r.transitDurationHours,
                r.transitSnr, r.signalDetectionEfficiencySde).map { it.takeIf { v -> v.isFinite() } })
        fun compare(a: Metrics, b: Metrics): List<String> {
            val names = listOf("period_days", "epoch_bjd", "depth_ppm", "duration_hours", "snr", "sde")
            return (if (a.detected != b.detected) listOf("detected") else emptyList()) +
                names.filterIndexed { i, _ -> a.values[i] != b.values[i] }
        }
    }
    private fun binding(id: String): String {
        val m = manifests.readVerified(id)
        return FitsEvidenceStore.sha256(m.toString().toByteArray(Charsets.UTF_8))
    }
    @Synchronized fun save(id: String, metrics: Metrics): String {
        val hash = binding(id)
        check(root.isDirectory || root.mkdirs())
        val run = UUID.randomUUID().toString()
        val body = "schema=1\nexperiment=$id\nmanifest=$hash\nrun=$run\ncreated=${System.currentTimeMillis()}\ndetected=${metrics.detected}\nvalues=${metrics.values.joinToString(",") { it?.toString() ?: "NA" }}\n"
        val payload = (body + FitsEvidenceStore.sha256(body.toByteArray(Charsets.UTF_8)) + "\n").toByteArray(Charsets.UTF_8)
        val used = checkNotNull(root.listFiles()).filter { it.isFile }.sumOf { it.length() }
        check(used <= maxBytes && payload.size.toLong() <= maxBytes - used) { "Result archive quota exceeded" }
        val temp = File.createTempFile("result-", ".tmp", root)
        try {
            temp.outputStream().use { it.write(payload); it.fd.sync() }
            check(temp.renameTo(File(root, "$run.result")))
        } finally { temp.delete() }
        return run
    }
    fun read(id: String, run: String): Metrics {
        require(UUID.fromString(run).toString() == run)
        val hash = binding(id)
        val file = File(root, "$run.result")
        require(file.isFile && file.length() in 1..8192L)
        val lines = file.readText(Charsets.UTF_8).removeSuffix("\n").split("\n")
        require(lines.size == 8)
        check(lines.last() == FitsEvidenceStore.sha256(lines.dropLast(1).joinToString("\n", postfix="\n").toByteArray(Charsets.UTF_8)))
        require(lines[0] == "schema=1" && lines[1] == "experiment=$id" && lines[2] == "manifest=$hash" && lines[3] == "run=$run")
        require(lines[4].startsWith("created=") && lines[4].substringAfter('=').toLong() > 0)
        require(lines[5] in listOf("detected=true", "detected=false") && lines[6].startsWith("values="))
        return Metrics(lines[5] == "detected=true", lines[6].substringAfter('=').split(',').map { if (it == "NA") null else it.toDouble() })
    }
}
