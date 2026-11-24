package com.synthesis.engine.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Executes registered handlers only. Completion is not scientific confirmation. */
class ResearchExecutor {
    enum class Outcome { COMPLETED, RETRYABLE_FAILURE, DEFERRED }
    data class Result(val queue: List<ResearchWorkQueue.Candidate>,
        val outcomes: List<Pair<ResearchWorkQueue.Work, Outcome>>)
    private val mutex = Mutex()
    suspend fun execute(items: List<ResearchWorkQueue.Candidate>,
        handlers: Map<PlanetResearchPlanner.Action, suspend (String) -> Outcome>,
        limit: Int = 5,
        checkpoint: suspend (List<ResearchWorkQueue.Candidate>) -> Unit): Result = mutex.withLock {
        val selected = ResearchWorkQueue.next(items.filter { it.actions.firstOrNull() in handlers.keys },limit)
        var queue = items.toList()
        val outcomes = mutableListOf<Pair<ResearchWorkQueue.Work, Outcome>>()
        for (work in selected) {
            val outcome = try {
                handlers.getValue(work.action)(work.candidateId)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Outcome.RETRYABLE_FAILURE }
            val updated = queue.map { item ->
                if (item.id != work.candidateId) item else when (outcome) {
                    Outcome.COMPLETED -> item.copy(actions=item.actions.drop(1),attempts=0)
                    Outcome.RETRYABLE_FAILURE -> item.copy(attempts=item.attempts+1)
                    Outcome.DEFERRED -> item
                }
            }
            checkpoint(updated)
            queue = updated
            outcomes.add(work to outcome)
        }
        Result(queue,outcomes)
    }
}
