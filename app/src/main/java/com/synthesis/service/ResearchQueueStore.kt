package com.synthesis.service

import com.synthesis.engine.discovery.ResearchWorkQueue
import com.synthesis.engine.discovery.PlanetResearchPlanner
import java.io.File

/** Local queue snapshot. A pending item is not a completed scientific analysis. */
class ResearchQueueStore(private val file: File) {
    companion object { private val lock = Any() }
    fun save(items: List<ResearchWorkQueue.Candidate>) = synchronized(lock) {
        require(items.size <= 1000)
        ResearchWorkQueue.next(items)
        require(items.all { it.id.matches(Regex("[A-Za-z0-9:_-]{1,200}")) })
        val body = items.joinToString("\n", prefix="schema=1\n", postfix="\n") {
            listOf(it.id,it.attempts.toString(),it.queuedAtMs.toString(),
                it.actions.joinToString(",") { action -> action.name }).joinToString("\t")
        }
        val bytes = (body + "sha256=" + FitsEvidenceStore.sha256(body.toByteArray(Charsets.UTF_8))).toByteArray(Charsets.UTF_8)
        val parent = requireNotNull(file.absoluteFile.parentFile)
        check(parent.isDirectory || parent.mkdirs())
        val temp = File.createTempFile("queue-", ".tmp", parent)
        try {
            temp.outputStream().use { it.write(bytes); it.fd.sync() }
            check(temp.renameTo(file.absoluteFile))
        } finally { temp.delete() }
    }
    fun load(): List<ResearchWorkQueue.Candidate> = synchronized(lock) {
        if (!file.exists()) return@synchronized emptyList()
        require(file.isFile && file.length() in 1..1048576L)
        val text = file.readText(Charsets.UTF_8)
        val body = text.substringBeforeLast("sha256=")
        check(text.substringAfterLast("sha256=") == FitsEvidenceStore.sha256(body.toByteArray(Charsets.UTF_8)))
        require(body.startsWith("schema=1\n"))
        val items = body.removePrefix("schema=1\n").lineSequence().filter { it.isNotEmpty() }.map {
            val fields = it.split('\t')
            require(fields.size == 4 && fields[0].matches(Regex("[A-Za-z0-9:_-]{1,200}")))
            ResearchWorkQueue.Candidate(fields[0], if (fields[3].isEmpty()) emptyList() else
                fields[3].split(',').map { name -> PlanetResearchPlanner.Action.valueOf(name) },
                fields[1].toInt(),fields[2].toLong())
        }.toList()
        require(items.size <= 1000)
        ResearchWorkQueue.next(items)
        items
    }
}
