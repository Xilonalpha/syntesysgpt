package com.synthesis.engine.discovery

import com.synthesis.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reserved discovery orchestrator. No independently validated discovery generation is implemented.
 */
@Singleton
class DiscoveryEngine @Inject constructor(
    private val patternDetector: PatternDetector,
    private val patentabilityEngine: PatentabilityEngine,
    private val commercialValueEstimator: CommercialValueEstimator
) {

    data class ResearchCandidate(
        val investigationId: String,
        val candidateId: String,
        val pattern: PatternDetector.DetectedPattern,
        val observationIds: List<String>,
        val requiredChecks: List<String>
    )

    /** Separate exploratory candidates from persisted discoveries. */
    suspend fun generateResearchCandidates(investigationId: String, observations: List<Observation>): List<ResearchCandidate> =
        withContext(Dispatchers.Default) {
            require(investigationId.isNotBlank())
            require(observations.all { it.investigationId == investigationId })
            require(observations.all { it.id.isNotBlank() })
            require(observations.map { it.id }.distinct().size == observations.size)
            val byId = observations.associateBy { it.id }
            patternDetector.detectHiddenPatterns(observations).map { pattern ->
                val ids = pattern.involvedObservations.distinct().sorted()
                require(ids.isNotEmpty() && ids.all { it in byId })
                val key = (listOf(investigationId, pattern.patternType.name) + ids).joinToString("") { "${it.length}:$it" }
                ResearchCandidate(
                    investigationId,
                    java.util.UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString(),
                    pattern,
                    ids,
                    listOf("Audit original acquisition and calibration",
                        "Repeat measurement independently",
                        "Quantify uncertainty and selection effects",
                        "Check existing literature and catalogs")
                )
            }
        }

    // Confirmed discoveries remain unavailable without independently validated evidence.
    suspend fun generateDiscoveries(investigationId: String, observations: List<Observation>): List<Discovery> {
        require(investigationId.isNotBlank())
        require(observations.all { it.investigationId == investigationId })
        return emptyList()
    }
}
