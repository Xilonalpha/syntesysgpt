package com.synthesis.engine.exoplanet

import com.synthesis.domain.model.exoplanet.AtmosphericCharacterizationScore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Atmospheric Transmission & Emission Spectroscopy Metric Engine
 *
 * Implements Kempton et al. (2018, PASP, 130:114401) standard metrics for JWST & Ariel exoplanet
 * atmospheric characterization triage.
 *
 * NASA pipeline blindspot:
 * Over 7,000 unconfirmed TOIs sit in the TFOP backlog without atmospheric suitability assessment.
 * Observatories and ground teams allocate expensive spectrographic time primarily to PI-sponsored targets,
 * leaving dozens of exceptional atmospheric transmission gems uncharacterized.
 */
@Singleton
class AtmosphericTsmEngine @Inject constructor(
    private val exoplanetCandidateFinder: ExoplanetCandidateFinder
) {

    private val KB = 1.380649e-23     // Boltzmann constant (J/K)
    private val AMU = 1.660539e-27    // Atomic mass unit (kg)
    private val G = 6.67430e-11
    private val M_EARTH_KG = 5.972e24
    private val R_EARTH_M = 6.3781e6
    private val R_SUN_M = 6.9634e8

    fun calculateAtmosphericScore(
        candidateName: String,
        ticId: String,
        planetRadiusEarth: Double,
        measuredMassEarth: Double?,
        equilibriumTempK: Double,
        stellarRadiusSun: Double,
        jMagnitude: Double,
        kMagnitude: Double? = null
    ): AtmosphericCharacterizationScore {
        require(listOf(planetRadiusEarth, equilibriumTempK, stellarRadiusSun).all { it.isFinite() && it > 0 })
        require(jMagnitude.isFinite() && (kMagnitude == null || kMagnitude.isFinite()))
        require(measuredMassEarth == null || (measuredMassEarth.isFinite() && measuredMassEarth > 0))
        // 1. Resolve or estimate planetary mass (M_earth)
        val resolvedMassEarth = measuredMassEarth
            ?: exoplanetCandidateFinder.estimateMassFromRadius(planetRadiusEarth)
            ?: throw IllegalArgumentException("Planetary mass unavailable")
        require(resolvedMassEarth.isFinite() && resolvedMassEarth > 0)

        // 2. Kempton et al. (2018) Scale Factor C based on radius regime
        val cFactor = when {
            planetRadiusEarth < 1.5 -> 0.190       // Terrestrial
            planetRadiusEarth < 2.75 -> 1.26       // Small sub-Neptune
            planetRadiusEarth < 4.0 -> 1.28        // Large sub-Neptune
            else -> 1.15                           // Sub-Jovian / Gas Giant
        }

        // 3. Transmission Spectroscopy Metric (TSM):
        // TSM = C * (Rp^3 * Teq) / (Mp * R*^2 * 10^(m_J / 5))
        val jFluxTerm = 10.0.pow(jMagnitude / 5.0)
        val tsmNumerator = cFactor * planetRadiusEarth.pow(3) * equilibriumTempK
        val tsmDenominator = resolvedMassEarth * stellarRadiusSun.pow(2) * jFluxTerm
        val tsm = if (tsmDenominator > 0) tsmNumerator / tsmDenominator else 0.0

        // 4. Emission Spectroscopy Metric (ESM) approximation (Kempton et al. 2018):
        // ESM = 4.29e6 * (B_7.5(T_day) / B_7.5(T_eff)) * (Rp / R*)^2 * 10^(-m_K / 5)
        val kmag = kMagnitude ?: (jMagnitude - 0.5) // Approximate J - K color if K is missing
        val daysideTempK = 1.10 * equilibriumTempK
        val esmPlanckRatio = (daysideTempK / 5000.0).pow(1.5).coerceIn(0.01, 2.0)
        val esm = (4.29e6 * esmPlanckRatio * (planetRadiusEarth * R_EARTH_M / (stellarRadiusSun * R_SUN_M)).pow(2) * 10.0.pow(-kmag / 5.0)).coerceIn(0.0, 500.0)

        // 5. Atmospheric Scale Height: H = (k_B * T_eq) / (mu * g)
        // Assume mean molecular weight: mu = 2.3 amu for sub-Neptunes, 18 amu for super-Earths, 28 amu for terrestrials
        val muAmu = when {
            planetRadiusEarth < 1.6 -> 28.0 // N2-dominated / volatile envelope
            planetRadiusEarth < 2.5 -> 18.0 // Water vapor / secondary
            else -> 2.3                     // Primordial H2/He
        }
        val muKg = muAmu * AMU
        val surfaceGravityMps2 = (G * (resolvedMassEarth * M_EARTH_KG)) / (planetRadiusEarth * R_EARTH_M).pow(2)
        val scaleHeightMeters = if (surfaceGravityMps2 > 0) (KB * equilibriumTempK) / (muKg * surfaceGravityMps2) else 10_000.0
        val scaleHeightKm = scaleHeightMeters / 1000.0

        // 6. Transit Atmospheric Feature Amplitude: Delta D ~ 2 * Rp * (5 * H) / R*^2 in ppm
        val deltaFeatureMeters2 = 2.0 * (planetRadiusEarth * R_EARTH_M) * (5.0 * scaleHeightMeters)
        val stellarAreaMeters2 = (stellarRadiusSun * R_SUN_M).pow(2)
        val featurePpm = (deltaFeatureMeters2 / stellarAreaMeters2) * 1_000_000.0

        // 7. JWST Observation Strategy Recommendation
        require(tsm.isFinite() && tsm > 0)
        val recommendation = "Exploratory TSM ranking only; molecular detection and observing time require instrument-specific simulation. Mass source: " +
            if (measuredMassEarth == null) "radius-based estimate" else "supplied mass"
        val percentile = Double.NaN // No reference population evaluated

        return AtmosphericCharacterizationScore(
            candidateName = candidateName,
            ticId = ticId,
            planetRadiusEarth = planetRadiusEarth,
            planetMassEarth = resolvedMassEarth,
            equilibriumTempK = equilibriumTempK,
            stellarRadiusSun = stellarRadiusSun,
            jMagnitude = jMagnitude,
            kMagnitude = kmag,
            tsmScore = tsm,
            esmScore = esm,
            atmosphericScaleHeightKm = scaleHeightKm,
            transitFeatureAmplitudePpm = featurePpm,
            jwstRecommendation = recommendation,
            rankPercentile = percentile
        )
    }
}
