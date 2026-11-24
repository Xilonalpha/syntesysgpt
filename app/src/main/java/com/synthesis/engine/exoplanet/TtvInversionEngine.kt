package com.synthesis.engine.exoplanet

import com.synthesis.domain.model.exoplanet.TtvCompanionAnalysis
import com.synthesis.domain.model.exoplanet.TtvObservedTransit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Transit Timing Variation (TTV) Gravitational Inversion Engine
 *
 * Implements celestial mechanics perturbation theory (Holman & Murray 2005, Agol et al. 2005, Lithwick et al. 2012)
 * to uncover non-transiting companion exoplanets that are completely invisible in direct transit surveys.
 *
 * NASA pipeline blindspot:
 * Standard BLS/TLS assumes strict linear ephemeris: T_n = T_0 + n * P.
 * When an unseen companion gravitationally tugs the transiting planet (producing O-C timing deviations of 10-180 minutes),
 * the phase-folded light curve is smeared out, destroying the signal-to-noise ratio and causing automated rejection.
 */
@Singleton
class TtvInversionEngine @Inject constructor() {

    private val M_SUN_KG = 1.989e30
    private val M_EARTH_KG = 5.972e24

    /**
     * Inverts O-C timing residuals to solve for unseen companion mass, period, and resonance.
     */
    fun analyzeTtvResiduals(
        candidateName: String,
        primaryPeriodDays: Double,
        primaryRadiusEarth: Double,
        stellarMassSun: Double,
        stellarRadiusSun: Double,
        stellarTeffK: Double,
        observedTransits: List<TtvObservedTransit>
    ): TtvCompanionAnalysis {
        throw UnsupportedOperationException("Scientific analysis unavailable: observational validation required")
    }


    data class TimingScatterDiagnostic(
        val ephemeris: LinearEphemeris,
        val reducedChiSquared: Double,
        val rmsMinutes: Double,
        val largestAbsoluteResidualMinutes: Double,
        val eventCount: Int
    )

    /** Descriptive excess timing scatter, not a companion detection or calibrated p-value. */
    fun diagnoseTimingScatter(observations: List<TransitTiming>): TimingScatterDiagnostic {
        require(observations.size >= 5)
        val fit = TransitTimingPhysics.fit(observations)
        val residuals = fit.residuals.map { it.residualMinutes }
        val reduced = fit.chiSquared / fit.degreesOfFreedom
        val rms = sqrt(residuals.sumOf { it * it } / residuals.size)
        require(reduced.isFinite() && reduced >= 0 && rms.isFinite())
        return TimingScatterDiagnostic(fit, reduced, rms, residuals.maxOf { abs(it) }, observations.size)
    }

    /** Conditional linear ephemeris; no companion inference. */
    fun fitMeasuredTimings(observations: List<TransitTiming>): LinearEphemeris =
        TransitTimingPhysics.fit(observations)
}
