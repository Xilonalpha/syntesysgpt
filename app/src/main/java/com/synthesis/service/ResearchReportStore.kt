package com.synthesis.service

import java.io.File

/** Local report integrity, not scientific certification. */
class ResearchReportStore(private val root: File, private val manifests: ExperimentManifestStore) {
    fun save(experimentId: String, report: String): String {
        manifests.readVerified(experimentId)
        val bytes = ("experiment=$experimentId\n" + report).toByteArray(Charsets.UTF_8)
        require(report.isNotBlank() && bytes.size <= 262144)
        val hash = FitsEvidenceStore.sha256(bytes)
        check(root.isDirectory || root.mkdirs())
        val target = File(root, "$hash.report")
        if (!target.exists()) {
            val temp = File.createTempFile("report-", ".tmp", root)
            try {
                temp.outputStream().use { it.write(bytes); it.fd.sync() }
                check(temp.renameTo(target))
            } finally { temp.delete() }
        }
        readVerified(hash)
        return hash
    }
    fun readVerified(hash: String): String {
        require(hash.matches(Regex("[0-9a-f]{64}")))
        val file = File(root, "$hash.report")
        require(file.isFile && file.length() in 1..262144L)
        val bytes = file.readBytes()
        check(FitsEvidenceStore.sha256(bytes) == hash)
        val text = bytes.toString(Charsets.UTF_8)
        val header = text.substringBefore('\n')
        require(header.startsWith("experiment="))
        manifests.readVerified(header.removePrefix("experiment="))
        return text
    }
}
