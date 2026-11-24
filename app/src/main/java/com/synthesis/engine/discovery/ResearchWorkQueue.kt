package com.synthesis.engine.discovery

/** Scheduling priority only, never a probability of discovery. */
object ResearchWorkQueue {
    data class Candidate(val id: String, val actions: List<PlanetResearchPlanner.Action>,
        val attempts: Int, val queuedAtMs: Long)
    data class Work(val candidateId: String, val action: PlanetResearchPlanner.Action)
    fun next(candidates: List<Candidate>, limit: Int = 5): List<Work> {
        require(limit in 1..50)
        require(candidates.all { it.id.isNotBlank() && it.attempts >= 0 && it.queuedAtMs >= 0 &&
            it.actions.distinct().size == it.actions.size })
        require(candidates.map { it.id }.distinct().size == candidates.size)
        return candidates.filter { it.actions.isNotEmpty() && it.attempts < 3 }
            .sortedWith(compareBy<Candidate> { it.attempts }.thenBy { it.queuedAtMs }.thenBy { it.id })
            .take(limit).map { Work(it.id, it.actions.first()) }
    }
}
