package com.synthesis.engine.exoplanet

import kotlin.math.sqrt

data class OrbitalAlias(val cycles: Int, val periodDays: Double, val periodErrorDays: Double)

/** Two epochs on the same time scale, conditional on a common strictly periodic source.
 * Bounds apply to nominal periods, not confidence intervals. No coverage or uniqueness claim.
 */
object OrbitalAliasPhysics {
    fun enumerate(firstEpoch: Double, secondEpoch: Double, firstErrorDays: Double,
                  secondErrorDays: Double, correlation: Double,
                  minPeriodDays: Double, maxPeriodDays: Double): List<OrbitalAlias> {
        require(listOf(firstEpoch, secondEpoch, firstErrorDays, secondErrorDays,
            correlation, minPeriodDays, maxPeriodDays).all { it.isFinite() })
        require(secondEpoch > firstEpoch && firstErrorDays > 0 && secondErrorDays > 0)
        require(correlation > -1 && correlation < 1)
        require(minPeriodDays > 0 && maxPeriodDays >= minPeriodDays)
        val delta = secondEpoch - firstEpoch
        require(delta.isFinite() && delta / minPeriodDays <= 100000)
        val variance = (firstErrorDays-secondErrorDays)*(firstErrorDays-secondErrorDays) +
            2*firstErrorDays*secondErrorDays*(1-correlation)
        require(variance.isFinite() && variance > 0)
        val error = sqrt(variance)
        return (1..kotlin.math.floor(delta/minPeriodDays).toInt()).mapNotNull { cycles ->
            val period = delta/cycles
            if (period in minPeriodDays..maxPeriodDays) OrbitalAlias(cycles, period, error/cycles) else null
        }
    }
}
