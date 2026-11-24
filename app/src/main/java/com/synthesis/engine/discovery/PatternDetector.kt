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

    /** Exploratory robust outliers, not calibrated significance or discovery. */
    fun detectHiddenPatterns(observations: List<Observation>): List<DetectedPattern> {
        if (observations.isEmpty()) return emptyList()
        require(observations.map { it.investigationId }.distinct().size == 1)
        require(observations.map { it.id }.distinct().size == observations.size)
        fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else sorted[mid-1]/2 + sorted[mid]/2
        }
        return observations.map { it to normalizer.normalize(it) }
            .filter { (_, n) -> n.measurement.isFinite() }
            .groupBy { (o, n) -> listOf(o.sourceId, o.entity, o.observationType.name, n.unit) }
            .values.flatMap { group ->
                if (group.size < 20) emptyList() else {
                    val center = median(group.map { it.second.measurement })
                    val mad = median(group.map { abs(it.second.measurement - center) })
                    val scale = 1.4826 * mad
                    if (!scale.isFinite() || scale <= 0) emptyList() else group.mapNotNull { (o, n) ->
                        val score = abs(n.measurement - center) / scale
                        if (!score.isFinite() || score < 5.0) null else DetectedPattern(
                            PatternType.STATISTICAL_OUTLIER,
                            "Exploratory MAD outlier; robust deviation=$score; n=${group.size}; significance uncalibrated",
                            0f, listOf(o.id), listOf(o.sourceId), listOf(o.observationType.name)
                        )
                    }
                }
            }
    }
}
