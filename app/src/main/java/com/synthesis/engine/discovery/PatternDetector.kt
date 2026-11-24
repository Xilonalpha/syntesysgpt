package com.synthesis.engine.discovery

import com.synthesis.domain.model.*
import com.synthesis.engine.normalizer.DataNormalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

@Singleton
class PatternDetector @Inject constructor(
    private val normalizer: DataNormalizer
) {

    data class DetectedPattern(
        val patternType: PatternType,
        val description: String,
        val significance: Float,
        val involvedObservations: List<String>,
        val involvedSources: List<String>,
        val domains: List<String>
    )

    // Unavailable until independently validated evidence and methodology exist.
    fun detectHiddenPatterns(observations: List<Observation>): List<DetectedPattern> = emptyList()
}
