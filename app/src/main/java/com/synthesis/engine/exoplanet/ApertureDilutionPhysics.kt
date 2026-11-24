package com.synthesis.engine.exoplanet

import kotlin.math.hypot

data class ApertureDilutionEstimate(val correctionFactor: Double, val depthPpm: Double, val depthErrorPpm: Double)

/** Conditional on the target being the eclipsed source; independent input errors, same passband. */
object ApertureDilutionPhysics {
    fun estimate(depthPpm: Double, depthErrorPpm: Double, targetFlux: Double,
                 targetError: Double, contaminantFlux: Double, contaminantError: Double): ApertureDilutionEstimate {
        require(listOf(depthPpm, depthErrorPpm, targetFlux, targetError, contaminantFlux, contaminantError).all { it.isFinite() })
        require(depthPpm > 0 && depthPpm < 1e6 && depthErrorPpm > 0 && targetFlux > 0 &&
            targetError > 0 && contaminantFlux >= 0 && contaminantError >= 0)
        val ratio = contaminantFlux / targetFlux
        val factor = 1 + ratio
        val factorError = hypot(contaminantError / targetFlux, ratio * (targetError / targetFlux))
        val corrected = depthPpm * factor
        val error = hypot(factor * depthErrorPpm, depthPpm * factorError)
        require(corrected.isFinite() && corrected <= 1e6 && error.isFinite())
        return ApertureDilutionEstimate(factor, corrected, error)
    }
}
