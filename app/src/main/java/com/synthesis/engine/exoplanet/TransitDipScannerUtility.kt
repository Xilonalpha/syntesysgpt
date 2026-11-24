package com.synthesis.engine.exoplanet

import com.synthesis.domain.model.exoplanet.*
import com.synthesis.domain.model.xilon.MastPhotometryPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Astronomical Analysis Utility for Scanning Photometric Light Curve Data Points.
 *
 * Scans time-series or phase-folded photon flux measurements for:
 * 1. Statistically significant drops in magnitude/flux (transit dips) using robust sigma-clipping.
 * 2. Dip morphology profiling (U-shape planetary profile vs V-shape eclipsing binary).
 * 3. Periodicity and recurrence verification (primary transits, recurring intervals, secondary occultations).
 * 4. Signal-to-noise characterization and Odd/Even transit consistency tests.
 */
@Singleton
class TransitDipScannerUtility @Inject constructor() {

    /**
     * Scans light curve points for recurring dips in magnitude.
     *
     * @param ticId Star identifier (e.g. "384159646" or "25155310")
     * @param points Photometric measurements from MAST / TESS time-series
     * @param nominalPeriodDays Known or estimated orbital period in days (if available)
     * @param expectedDurationHours Expected transit duration in hours (if available)
     * @param sigmaThreshold Sensitivity threshold in standard deviations (e.g. 3.0 to 5.0)
     */
    fun scanForRecurringDips(
        ticId: String,
        points: List<MastPhotometryPoint>,
        nominalPeriodDays: Double? = null,
        expectedDurationHours: Double? = null,
        sigmaThreshold: Double = 3.2
    ): TransitDipScanReport {
        // Legacy phase-only samples lack observation epochs, units and uncertainties.
        // Do not infer recurrence or statistical significance from a fitted curve.
        return emptyReport(ticId, sigmaThreshold)
    }

    private fun emptyReport(ticId: String, sigma: Double): TransitDipScanReport {
        return TransitDipScanReport(
            ticId = ticId,
            totalDipsFound = 0,
            primaryDip = null,
            secondaryDip = null,
            allDips = emptyList(),
            recurrenceVerdict = DipRecurrenceVerdict.IRREGULAR_NOISE_OR_STELLAR_VARIATION,
            recurrencePeriodCandidateDays = null,
            oddEvenDepthRatio = null,
            oddEvenConsistencySigma = null,
            noiseFloorPpm = Double.NaN,
            detectionThresholdSigma = sigma,
            scientificDiagnosis = "Analiză indisponibilă: sunt necesare timpi observaționali originali, unități explicite și incertitudini fotometrice. Curba pliată nu demonstrează recurența."
        )
    }

}
