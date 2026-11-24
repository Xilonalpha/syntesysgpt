package com.synthesis.engine.hypothesis

import com.synthesis.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HypothesisEngine @Inject constructor() {

    /** Exploratory evidence links; no automatic confidence or confirmation. */
    fun patternEvidence(observations: List<Observation>): List<com.synthesis.engine.discovery.PatternDetector.DetectedPattern> =
        com.synthesis.engine.discovery.PatternDetector(com.synthesis.engine.normalizer.DataNormalizer()).detectHiddenPatterns(observations)

    data class HypothesisResult(
        val title: String,
        val description: String,
        val evidenceCount: Int,
        val contradictionCount: Int,
        val confidence: Float,
        val discriminatingTests: List<DiscriminatingTest>,
        val status: HypothesisStatus
    )

    fun generateHypotheses(
        observations: List<Observation>,
        anomalies: List<Anomaly>,
        correlations: List<Correlation>
    ): List<Hypothesis> {
        val investigationId = observations.firstOrNull()?.investigationId ?: return emptyList()
        require(investigationId.isNotBlank())
        require(observations.all { it.investigationId == investigationId })
        require(anomalies.all { it.investigationId == investigationId })
        require(correlations.all { it.investigationId == investigationId })
        require(observations.all { it.id.isNotBlank() })
        val observationIds = observations.map { it.id }.toSet()
        require(observationIds.size == observations.size)
        require(anomalies.all { it.observationId in observationIds })
        fun proposal(title: String, description: String, test: String, data: String) = Hypothesis(
            id = java.util.UUID.nameUUIDFromBytes((investigationId + ":hypothesis:" + title).toByteArray(Charsets.UTF_8)).toString(), investigationId = investigationId,
            title = title, description = description, evidenceCount = 0,
            contradictionCount = 0, confidence = 0f,
            discriminatingTests = listOf(DiscriminatingTest(test, data, "Not established", "Not scheduled")),
            status = HypothesisStatus.PENDING
        )
        val patterns = patternEvidence(observations)
        val proposals = listOf(
            proposal("Known phenomenon", "Possible explanation; not evaluated.", "Compare against documented physical models", "Original observations and model uncertainties"),
            proposal("Previously unclassified phenomenon", "Novelty has not been established.", "Check independent observations and catalogs", "Independent data and catalog coverage"),
            proposal("Measurement artifact", "Instrumental effects have not been excluded.", "Reprocess and check calibration", "Raw data and calibration records"),
            proposal("Data synchronization problem", "Timing and coordinate errors have not been excluded.", "Audit time and coordinate references", "Original metadata")
        )
        return if (patterns.isEmpty()) proposals else proposals + proposal(
            "Robust outlier follow-up",
            "Exploratory flags for observation IDs: " + patterns.flatMap { it.involvedObservations }.distinct().sorted().joinToString(),
            "Repeat measurements and test calibration and selection effects",
            "Independent measurements and instrument uncertainty"
        )
    }

    fun generateNextActions(hypotheses: List<Hypothesis>): List<String> =
        hypotheses.flatMap { it.discriminatingTests }.map { it.description }.distinct()
}
