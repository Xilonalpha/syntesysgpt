package com.synthesis.engine.anomaly

import com.synthesis.domain.model.*
import com.synthesis.engine.normalizer.DataNormalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow

@Singleton
class AnomalyDetector @Inject constructor(
    private val normalizer: DataNormalizer
) {

    data class AnomalyResult(
        val observation: Observation,
        val expectedValue: Double,
        val deviation: Double,
        val deviationPercent: Double,
        val anomalyScore: Float,
        val possibleCauses: List<PossibleCause>,
        val severity: AnomalySeverity
    )

    fun detectAnomalies(observations: List<Observation>): List<Anomaly> {
        if (observations.size < 10 || observations.map { it.investigationId }.distinct().size != 1) return emptyList()

        val normalized = normalizer.normalizeBatch(observations.distinctBy { it.id })
        // Group by observationType AND physical unit so we don't mix temperatures with wind speeds or pressures
        val groupedByMetric = normalized.groupBy { listOf(it.observationType.name, it.unit, it.entity, it.source) }

        val anomalies = mutableListOf<Anomaly>()

        groupedByMetric.forEach { (typeUnitPair, rawRecords) ->
            val (type, unit) = typeUnitPair
            // Missing-data sentinels must be handled by the source-specific parser.
            val records = rawRecords.filter { !it.measurement.isNaN() && !it.measurement.isInfinite()  }
            if (records.size < 10) return@forEach

            val values = records.map { it.measurement }
            val mean = values.average()
            if (!mean.isFinite()) return@forEach
            val stdDev = calculateStdDev(values, mean)
            if (!stdDev.isFinite() || stdDev <= 0) return@forEach

            val seenObservations = mutableSetOf<String>()
            records.forEachIndexed { index, record ->
                if (!seenObservations.add(record.id)) return@forEachIndexed
                val zScore = if (stdDev > 0) abs(record.measurement - mean) / stdDev else 0.0
                if (!zScore.isFinite()) return@forEachIndexed
                val score = (zScore / 3.0).coerceIn(0.0, 1.0).toFloat()

                if (zScore >= 3.0) {
                    val deviation = record.measurement - mean
                    val deviationPercent = when (unit) {
                        "°C" -> (deviation / (mean + 273.15)) * 100.0 // Thermodynamic ratio in Kelvin
                        else -> if (abs(mean) > 1e-6) (deviation / mean) * 100.0 else Double.NaN
                    }

                    anomalies.add(
                        Anomaly(
                            id = java.util.UUID.nameUUIDFromBytes(("anomaly:" + observations.first().investigationId + ":" + record.id).toByteArray(Charsets.UTF_8)).toString(),
                            investigationId = observations.firstOrNull()?.investigationId ?: "",
                            observationId = record.id,
                            expectedValue = mean,
                            observedValue = record.measurement,
                            deviation = deviation,
                            deviationPercent = deviationPercent,
                            anomalyScore = score,
                            possibleCauses = emptyList(),
                            severity = AnomalySeverity.LOW
                        )
                    )
                }
            }
        }

        return anomalies.sortedByDescending { it.anomalyScore }
    }

    private fun calculateStdDev(values: List<Double>, mean: Double): Double {
        if (values.size <= 1) return 0.0
        val sumSquared = values.sumOf { (it - mean).pow(2) }
        val sampleVariance = sumSquared / (values.size - 1) // Unbiased sample variance (Bessel's correction N-1)
        return kotlin.math.sqrt(sampleVariance)
    }

}
