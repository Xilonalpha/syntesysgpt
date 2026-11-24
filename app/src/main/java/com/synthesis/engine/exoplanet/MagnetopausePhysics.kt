package com.synthesis.engine.exoplanet

import kotlin.math.PI
import kotlin.math.pow

data class MagnetopauseEstimate(val ramPressureNanoPa: Double,
    val standoffPlanetRadii: Double, val balanceAboveSurface: Boolean)

/** Idealized equatorial vacuum dipole balanced against proton ram pressure.
 * Neglects thermal and magnetic wind pressure, currents and reconnection.
 * Not an atmospheric retention or habitability estimate.
 */
object MagnetopausePhysics {
    fun evaluate(equatorialFieldTesla: Double, protonDensityCm3: Double,
        windSpeedKms: Double): MagnetopauseEstimate {
        require(listOf(equatorialFieldTesla, protonDensityCm3, windSpeedKms)
            .all { it.isFinite() && it > 0 })
        val speed = windSpeedKms * 1000.0
        val pressure = protonDensityCm3 * 1e6 * 1.67262192369e-27 * speed * speed
        val magneticPressure = equatorialFieldTesla * equatorialFieldTesla / (2 * 4 * PI * 1e-7)
        val ratio = magneticPressure / pressure
        val radius = ratio.pow(1.0 / 6.0)
        require(pressure.isFinite() && pressure > 0 && radius.isFinite() && radius > 0)
        val nanoPa = pressure * 1e9
        require(nanoPa.isFinite() && nanoPa > 0)
        return MagnetopauseEstimate(nanoPa, radius, radius > 1.0)
    }
}
