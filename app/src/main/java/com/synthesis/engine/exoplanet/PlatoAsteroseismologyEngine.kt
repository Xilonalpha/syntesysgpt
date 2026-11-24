package com.synthesis.engine.exoplanet

import com.synthesis.domain.model.exoplanet.PlatoAsteroseismicResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * ESA PLATO Asteroseismology & Stellar Age Inversion Engine (2026 Edition)
 *
 * Implements asteroseismic scaling relations (Huber et al. 2011, Chaplin & Miglio 2013)
 * to estimate stellar mass, radius and density,
 * without claiming calibrated exoplanet radius precision
 * or habitability.
 */
@Singleton
class PlatoAsteroseismologyEngine @Inject constructor() {

    /** First-order independent measurement errors; excludes scaling-relation systematics. */
    data class FractionalErrors(val mass: Double, val radius: Double, val density: Double, val planetRadius: Double)
    fun propagateFractionalErrors(nuMax: Double, deltaNu: Double, temperature: Double, depth: Double): FractionalErrors {
        require(listOf(nuMax, deltaNu, temperature, depth).all { it.isFinite() && it >= 0 && it <= 0.1 })
        val mass = sqrt(9*nuMax*nuMax + 16*deltaNu*deltaNu + 2.25*temperature*temperature)
        val radius = sqrt(nuMax*nuMax + 4*deltaNu*deltaNu + 0.25*temperature*temperature)
        return FractionalErrors(mass, radius, 2*deltaNu, kotlin.math.hypot(radius, depth/2))
    }

    companion object {
        // Solar benchmark asteroseismic values (Huber et al.)
        const val NU_MAX_SUN = 3090.0 // microHz
        const val DELTA_NU_SUN = 135.1 // microHz
        const val TEFF_SUN = 5777.0 // K
        const val LOG_G_SUN = 4.438 // dex (cgs)
        const val R_SUN_IN_R_EARTH = 109.209
    }

    /**
     * Solves stellar and planetary parameters using acoustic p-mode frequencies.
     *
     * @param starName Name or catalog identifier of host star
     * @param ticId TESS / PLATO Input Catalog ID
     * @param nuMaxMicroHz Frequency of maximum oscillation power (nu_max in microHz)
     * @param deltaNuMicroHz Large frequency separation (Delta_nu in microHz)
     * @param teffK Stellar effective temperature in Kelvin
     * @param metallicityFeH Stellar metallicity [Fe/H] (dex)
     * @param observedTransitDepthPpm Transit depth in parts-per-million
     */
    fun solveStellarAndPlanetaryProperties(
        starName: String,
        ticId: String,
        nuMaxMicroHz: Double,
        deltaNuMicroHz: Double,
        teffK: Double,
        metallicityFeH: Double = 0.0,
        observedTransitDepthPpm: Double
    ): PlatoAsteroseismicResult {
        require(listOf(nuMaxMicroHz, deltaNuMicroHz, teffK).all { it.isFinite() && it > 0 })
        require(metallicityFeH.isFinite() && observedTransitDepthPpm.isFinite() && observedTransitDepthPpm > 0.0 && observedTransitDepthPpm < 1000000.0)
        val nuMaxRatio = nuMaxMicroHz / NU_MAX_SUN
        val deltaNuRatio = deltaNuMicroHz / DELTA_NU_SUN
        val teffRatio = teffK / TEFF_SUN

        // 1. Asteroseismic Scaling Relations (Chaplin et al. 2011)
        // M / M_sun = (nu_max / nu_max_sun)^3 * (Delta_nu / Delta_nu_sun)^(-4) * (Teff / Teff_sun)^(3/2)
        val stellarMassSun = (nuMaxRatio.pow(3)) * (deltaNuRatio.pow(-4)) * (teffRatio.pow(1.5))

        // R / R_sun = (nu_max / nu_max_sun) * (Delta_nu / Delta_nu_sun)^(-2) * (Teff / Teff_sun)^(1/2)
        val stellarRadiusSun = nuMaxRatio * (deltaNuRatio.pow(-2)) * sqrt(teffRatio)

        // Mean stellar density: rho / rho_sun = (Delta_nu / Delta_nu_sun)^2
        val meanStellarDensitySun = deltaNuRatio.pow(2)

        // Surface gravity: log(g) = log(g_sun) + log10(nu_max/nu_max_sun * sqrt(Teff/Teff_sun))
        val surfaceGravityLogG = LOG_G_SUN + log10(nuMaxRatio * sqrt(teffRatio))

        require(listOf(stellarMassSun, stellarRadiusSun, meanStellarDensitySun).all { it.isFinite() && it > 0 })
        require(surfaceGravityLogG.isFinite())
        val stellarAgeGyr = Double.NaN // Requires evolutionary tracks and measured uncertainties
        val mainSequenceLifetimeGyr = Double.NaN
        val planetRadiusEarth = R_SUN_IN_R_EARTH * sqrt(observedTransitDepthPpm / 1e6) * stellarRadiusSun
        require(planetRadiusEarth.isFinite() && planetRadiusEarth > 0)
        val planetRadiusErrorPercent = Double.NaN
        val isOldEnoughForLife = false // Not assessed
        val habitabilityVerdict = "Habitability and stellar age not assessed."
        val summary = "Uncalibrated asteroseismic scaling estimates; age and radius uncertainty require additional data."

        return PlatoAsteroseismicResult(
            starName = starName,
            ticId = ticId,
            nuMaxMicroHz = nuMaxMicroHz,
            deltaNuMicroHz = deltaNuMicroHz,
            teffK = teffK,
            metallicityFeH = metallicityFeH,
            observedTransitDepthPpm = observedTransitDepthPpm,
            stellarMassSun = stellarMassSun,
            stellarRadiusSun = stellarRadiusSun,
            meanStellarDensitySun = meanStellarDensitySun,
            surfaceGravityLogG = surfaceGravityLogG,
            stellarAgeGyr = stellarAgeGyr,
            mainSequenceLifetimeGyr = mainSequenceLifetimeGyr,
            planetRadiusEarth = planetRadiusEarth,
            planetRadiusErrorPercent = planetRadiusErrorPercent,
            isOldEnoughForLife = isOldEnoughForLife,
            habitabilityVerdict = habitabilityVerdict,
            asteroseismicMethodSummary = summary
        )
    }
}
