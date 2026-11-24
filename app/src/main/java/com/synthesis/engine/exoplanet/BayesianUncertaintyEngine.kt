package com.synthesis.engine.exoplanet

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/** First-order physical error propagation; no calibrated detection posterior. */
@Singleton
class BayesianUncertaintyEngine @Inject constructor() {

    fun propagatePhysicalUncertainties(
        depthPpm: Double,
        depthErrorPpm: Double,
        periodDays: Double,
        periodErrorDays: Double,
        stellarRadiusSolar: Double,
        stellarRadiusErrorSolar: Double,
        stellarMassSolar: Double,
        stellarMassErrorSolar: Double,
        stellarTeffK: Double,
        stellarTeffErrorK: Double,
        chiSquared: Double,
        degreesOfFreedom: Int
    ): com.synthesis.domain.model.exoplanet.PhysicalUncertaintyQuantification {
        require(listOf(depthPpm, periodDays, stellarRadiusSolar, stellarMassSolar, stellarTeffK).all { it.isFinite() && it > 0.0 })
        require(depthPpm < 1_000_000.0)
        require(listOf(depthErrorPpm, periodErrorDays, stellarRadiusErrorSolar, stellarMassErrorSolar, stellarTeffErrorK).all { it.isFinite() && it > 0.0 })
        require(chiSquared.isFinite() && chiSquared >= 0.0 && degreesOfFreedom > 0)
        val depthFrac = (depthPpm / 1_000_000.0)
        val depthFracErr = (depthErrorPpm / 1_000_000.0)

        // 1. Planetary Radius (R_Earth) = R_star * sqrt(depth) * 109.1
        val rEarthRatio = 109.1
        val radiusEarth = stellarRadiusSolar * sqrt(depthFrac) * rEarthRatio
        val relDepthTerm = 0.5 * (depthFracErr / depthFrac)
        val relRStarTerm = (stellarRadiusErrorSolar / stellarRadiusSolar)
        val radiusErrEarth = radiusEarth * sqrt(relDepthTerm.pow(2) + relRStarTerm.pow(2))

        // 2. Semi-Major Axis (AU) = (M_star * (P / 365.25)^2)^(1/3)
        val periodYears = periodDays / 365.25
        val semiMajorAxisAu = (stellarMassSolar * periodYears.pow(2)).pow(1.0 / 3.0)
        val relMassTerm = (1.0 / 3.0) * (stellarMassErrorSolar / stellarMassSolar)
        val relPeriodTerm = (2.0 / 3.0) * (periodErrorDays / periodDays)
        val semiMajorAxisErrAu = semiMajorAxisAu * sqrt(relMassTerm.pow(2) + relPeriodTerm.pow(2))

        // 3. Equilibrium Temperature (K) = T_star * sqrt(R_star * R_sun / (2 * a * AU)) * (1 - A)^0.25
        // With standard Bond albedo A = 0.3 -> (1 - 0.3)^0.25 ~ (1.0 - 0.3).pow(0.25)
        val rSunInAu = 0.00465047
        val teqK = stellarTeffK * sqrt((stellarRadiusSolar * rSunInAu) / (2.0 * semiMajorAxisAu)) * (1.0 - 0.3).pow(0.25)
        val relTeffTerm = stellarTeffErrorK / stellarTeffK
        val relATerm = 0.5 * (semiMajorAxisErrAu / semiMajorAxisAu)
        val teqErrK = teqK * sqrt(relTeffTerm.pow(2) + (0.5 * relRStarTerm).pow(2) + relATerm.pow(2))

        val redChi2 = chiSquared / degreesOfFreedom

        return com.synthesis.domain.model.exoplanet.PhysicalUncertaintyQuantification(
            planetRadiusEarth = radiusEarth,
            planetRadiusError1Sigma = radiusErrEarth,
            semiMajorAxisAu = semiMajorAxisAu,
            semiMajorAxisError1Sigma = semiMajorAxisErrAu,
            equilibriumTempK = teqK,
            equilibriumTempError1Sigma = teqErrK,
            periodDays = periodDays,
            periodError1Sigma = periodErrorDays,
            transitDepthPpm = depthPpm,
            depthError1Sigma = depthErrorPpm,
            reducedChiSquared = redChi2,
            degreesOfFreedom = degreesOfFreedom
        )
    }
}
