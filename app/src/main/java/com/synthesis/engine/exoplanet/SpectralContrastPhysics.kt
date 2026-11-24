package com.synthesis.engine.exoplanet

import kotlin.math.sqrt
import kotlin.math.hypot

data class MeasuredSpectralBin(val wavelengthMicrons: Double, val depthPpm: Double, val errorPpm: Double)
data class SpectralContrast(val contrastPpm: Double, val errorPpm: Double, val signedSigma: Double)

/** Preselected disjoint bands, independent errors and locally flat continuum.
 * No molecular identification or global false-alarm probability.
 */
object SpectralContrastPhysics {
    fun evaluate(bins: List<MeasuredSpectralBin>, feature: ClosedFloatingPointRange<Double>,
                 continuum: ClosedFloatingPointRange<Double>): SpectralContrast {
        require(listOf(feature.start, feature.endInclusive, continuum.start, continuum.endInclusive)
            .all { it.isFinite() && it > 0 })
        require(feature.start < feature.endInclusive && continuum.start < continuum.endInclusive)
        require(feature.endInclusive < continuum.start || continuum.endInclusive < feature.start)
        require(bins.all { it.wavelengthMicrons.isFinite() && it.wavelengthMicrons > 0 &&
            it.depthPpm.isFinite() && it.errorPpm.isFinite() && it.errorPpm > 0 })
        require(bins.map { it.wavelengthMicrons }.distinct().size == bins.size)
        fun mean(range: ClosedFloatingPointRange<Double>): Pair<Double, Double> {
            val selected = bins.filter { it.wavelengthMicrons in range }
            require(selected.size >= 2)
            val weights = selected.map { 1.0 / (it.errorPpm * it.errorPpm) }
            val sum = weights.sum()
            require(sum.isFinite() && sum > 0 && weights.all { it.isFinite() && it > 0 })
            val value = selected.indices.sumOf { (weights[it]/sum)*selected[it].depthPpm }
            return value to sqrt(1.0/sum)
        }
        val (f, fe) = mean(feature)
        val (c, ce) = mean(continuum)
        val contrast = f-c
        val error = hypot(fe, ce)
        val sigma = contrast/error
        require(contrast.isFinite() && error.isFinite() && error > 0 && sigma.isFinite())
        return SpectralContrast(contrast, error, sigma)
    }
}
