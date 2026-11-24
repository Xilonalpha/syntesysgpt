package com.synthesis.engine.validation

import kotlin.math.abs
import kotlin.math.hypot

/** Exploratory comparison of measurements of the same quantity, not a novelty test. */
object IndependentMeasurementComparison {
    data class Input(
        val observationId: String,
        val acquisitionId: String,
        val sourceId: String,
        val quantityKey: String,
        val unit: String,
        val value: Double,
        val standardUncertainty: Double
    )
    enum class Status { INSUFFICIENT_PROVENANCE, INCOMPARABLE, CONSISTENT, DISCREPANT }
    data class Result(val status: Status, val standardizedDifference: Double?)

    /** Caller must establish independence and matching target, epoch and physical quantity. */
    fun compare(a: Input, b: Input, independenceEstablished: Boolean): Result {
        if (!independenceEstablished || listOf(a,b).any {
            it.observationId.isBlank() || it.acquisitionId.isBlank() || it.sourceId.isBlank()
        } || a.observationId == b.observationId || a.acquisitionId == b.acquisitionId)
            return Result(Status.INSUFFICIENT_PROVENANCE, null)
        if (a.quantityKey.isBlank() || a.unit.isBlank() || a.quantityKey != b.quantityKey ||
            a.unit != b.unit || listOf(a,b).any {
                !it.value.isFinite() || !it.standardUncertainty.isFinite() || it.standardUncertainty <= 0
            }) return Result(Status.INCOMPARABLE, null)
        val uncertainty = hypot(a.standardUncertainty, b.standardUncertainty)
        val z = abs(a.value - b.value) / uncertainty
        if (!uncertainty.isFinite() || !z.isFinite()) return Result(Status.INCOMPARABLE, null)
        return Result(if (z <= 3.0) Status.CONSISTENT else Status.DISCREPANT, z)
    }
}
