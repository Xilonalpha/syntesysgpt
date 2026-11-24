package com.synthesis.engine.exoplanet

import com.synthesis.domain.model.exoplanet.PhaseFoldedPoint
import kotlin.math.*

/** Fixed-duration box profile with fitted baseline and depth.
 * Errors are conditional on independent Gaussian photometric errors and fixed duration.
 * Not calibrated for correlated noise or finite exposure integration.
 */
object IndividualTransitTiming {
    fun estimate(points: List<PhaseFoldedPoint>, periodDays: Double, epochBjd: Double,
        durationDays: Double): List<TransitTiming> {
        require(periodDays.isFinite() && periodDays > 0 && epochBjd.isFinite() && epochBjd > 2400000)
        require(durationDays.isFinite() && durationDays > 0 && durationDays < periodDays / 4)
        return points.filter { it.bjd.isFinite() && it.relativeFlux.isFinite() &&
            it.fluxError.isFinite() && it.fluxError > 0 }
            .groupBy { it.epochIndex }.mapNotNull { (cycle, group) ->
                if (cycle !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return@mapNotNull null
                val center = epochBjd + cycle * periodDays
                val local = group.filter { abs(it.bjd - center) <= 1.5 * durationDays }.sortedBy { it.bjd }
                if (local.size < 12 || local.zipWithNext().any { (a,b) -> a.bjd >= b.bjd }) return@mapNotNull null
                val gaps = local.zipWithNext().map { (a,b) -> b.bjd-a.bjd }.sorted()
                val cadence = gaps[gaps.size/2]
                if (cadence > durationDays / 4 || gaps.any { it > 2.5 * cadence }) return@mapNotNull null
                val step = durationDays / 80
                val profile = (0..80).map { i ->
                    val shift = (i-40)*step
                    val inside = local.filter { abs(it.bjd-center-shift) <= durationDays/2 }
                    val before = local.filter { it.bjd-center-shift < -durationDays/2 }
                    val after = local.filter { it.bjd-center-shift > durationDays/2 }
                    if (inside.size < 3 || before.size < 3 || after.size < 3) Double.POSITIVE_INFINITY
                    else {
                        val outside = before + after
                        fun mean(p: List<PhaseFoldedPoint>): Double {
                            val w = p.map { 1.0/(it.fluxError*it.fluxError) }
                            return p.indices.sumOf { w[it]*p[it].relativeFlux } / w.sum()
                        }
                        val low = mean(inside)
                        val high = mean(outside)
                        if (!(high > low)) Double.POSITIVE_INFINITY
                        else inside.sumOf { ((it.relativeFlux-low)/it.fluxError).pow(2) } +
                            outside.sumOf { ((it.relativeFlux-high)/it.fluxError).pow(2) }
                    }
                }
                val best = profile.indices.minByOrNull { profile[it] } ?: return@mapNotNull null
                val minimum = profile[best]
                if (!minimum.isFinite() || best == 0 || best == 80) return@mapNotNull null
                val accepted = profile.indices.filter { profile[it] <= minimum+1 }
                val left = accepted.first()
                val right = accepted.last()
                if (left == 0 || right == 80 || accepted.size != right-left+1) return@mapNotNull null
                val error = maxOf(cadence/2, (best-left+1)*step, (right-best+1)*step)
                TransitTiming(cycle.toInt(), center+(best-40)*step, error)
            }.sortedBy { it.cycle }
    }
}
