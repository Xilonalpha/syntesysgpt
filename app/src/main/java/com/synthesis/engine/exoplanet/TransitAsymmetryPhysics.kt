package com.synthesis.engine.exoplanet

import kotlin.math.abs
import kotlin.math.sqrt

data class TransitAsymmetryEstimate(
    val signedAsymmetry: Double,
    val asymmetryError: Double,
    val differenceSigma: Double
)

/** Descriptive photometric statistic; does not identify the occulting object. */
object TransitAsymmetryPhysics {
    fun evaluate(ingressHours: Double, egressHours: Double,
                 ingressErrorHours: Double, egressErrorHours: Double,
                 correlation: Double): TransitAsymmetryEstimate {
        require(listOf(ingressHours, egressHours, ingressErrorHours, egressErrorHours, correlation).all { it.isFinite() })
        require(ingressHours > 0 && egressHours > 0 && ingressErrorHours > 0 && egressErrorHours > 0)
        require(correlation > -1 && correlation < 1)
        val sum = ingressHours + egressHours
        require(sum.isFinite())
        val a = ingressHours / sum
        val b = egressHours / sum
        val x = 2 * b * (ingressErrorHours / sum)
        val y = 2 * a * (egressErrorHours / sum)
        val variance = (x-y)*(x-y) + 2*x*y*(1-correlation)
        val differenceVariance = (ingressErrorHours-egressErrorHours)*(ingressErrorHours-egressErrorHours) +
            2*ingressErrorHours*egressErrorHours*(1-correlation)
        require(variance.isFinite() && variance > 0 && differenceVariance.isFinite() && differenceVariance > 0)
        val sigma = abs(ingressHours-egressHours)/sqrt(differenceVariance)
        require(sigma.isFinite())
        return TransitAsymmetryEstimate(a-b, sqrt(variance), sigma)
    }
}
