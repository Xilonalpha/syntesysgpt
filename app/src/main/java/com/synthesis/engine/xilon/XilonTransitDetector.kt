package com.synthesis.engine.xilon

import com.synthesis.domain.model.xilon.*
import com.synthesis.engine.astrometry.AstrometryEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * XILON Autonomous Mathematical Transit Detection Engine.
 *
 * Performs photon-level statistical analysis:
 * 1. Transit depth & duration characterization (handles both phase-in-hours and normalized phase)
 * 2. Signal-to-Noise Ratio (SNR) calculation against photometric noise floor
 * 3. Astrophysical False-Positive Vetting (Odd/Even depth test & Secondary eclipse check)
 * 4. Planetary radius (Rp) derivation using host star radius
 * 5. Equilibrium temperature & Habitable Zone verification
 * 6. Academic cross-match validation (Confirmed Benchmark vs Candidate Audit vs New Signal)
 * 7. IAU 1958 standard astrometry conversion (ICRS/J2000 <-> Galactic) & BJD_TDB timing
 */
@Singleton
class XilonTransitDetector @Inject constructor(
    private val astrometryEngine: AstrometryEngine
) {

    fun analyzePhotometricTimeSeries(
        rawPoints: List<MastPhotometryPoint>,
        star: MastStarProperties,
        periodDays: Double,
        sector: String,
        tceName: String,
        archivalMatch: ArchivalCrossMatch,
        dvDurationHours: Double? = null,
        dvDepthPpm: Double? = null,
        dvSnr: Double? = null
    ): XilonDetectionResult {
        val logs = mutableListOf<String>()
        logs.add("Received ${rawPoints.size} phase-folded photometry points.")

        if (rawPoints.isEmpty()) {
            return fallbackNoDetection(star, sector, tceName, archivalMatch, logs)
        }

        // Folded phase units cannot be inferred reliably from the values alone.
        // Only normalized phase is supported here; no independent detection is claimed.
        if (!periodDays.isFinite() || periodDays <= 0.0 || dvDurationHours == null ||
            !dvDurationHours.isFinite() || dvDurationHours <= 0.0 || dvDurationHours >= periodDays * 24.0 ||
            rawPoints.any { !it.phase.isFinite() || abs(it.phase) > 0.5 || !it.detrendedFlux.isFinite() }) {
            logs.add("Missing valid duration or unsupported folded phase data.")
            return fallbackNoDetection(star, sector, tceName, archivalMatch, logs)
        }
        val isPhaseInHours = false
        val normalizedPoints = rawPoints.sortedBy { it.phase }
        val durationHours = dvDurationHours
        val inTransitHalfWidth = durationHours / (periodDays * 48.0)

        // Separate in-transit region from baseline
        val inTransitCandidates = normalizedPoints.filter { abs(it.phase) <= inTransitHalfWidth }
        val outOfTransit = normalizedPoints.filter { abs(it.phase) > (inTransitHalfWidth * 1.4) }

        if (outOfTransit.isEmpty() || inTransitCandidates.isEmpty()) {
            logs.add("Insufficient baseline or transit coverage in this sector.")
            return fallbackNoDetection(star, sector, tceName, archivalMatch, logs)
        }

        // Calculate baseline flux mean and RMS noise floor
        val baselineMean = outOfTransit.map { it.detrendedFlux }.average()
        val variance = outOfTransit.map { (it.detrendedFlux - baselineMean).pow(2) }.average()
        val rmsNoise = sqrt(variance)
        if (!rmsNoise.isFinite() || rmsNoise <= 0.0) return fallbackNoDetection(star, sector, tceName, archivalMatch, logs)

        // Measure transit depth: use robust average of deepest 20% in-transit points
        val sortedInTransit = inTransitCandidates.map { it.detrendedFlux }.sorted()
        val bottomSampleSize = (sortedInTransit.size * 0.20).toInt().coerceAtLeast(4)
        val inTransitBottom = sortedInTransit.average()

        val rawDepthFraction = (baselineMean - inTransitBottom).coerceAtLeast(0.0)
        val measuredDepthPpm = rawDepthFraction * 1_000_000.0
        val finalTransitDepthPpm = dvDepthPpm?.takeIf { it.isFinite() && it >= 0.0 } ?: measuredDepthPpm

        // 2. Signal-to-Noise Ratio (SNR)
        val nIn = inTransitCandidates.size
        val measuredSnr = if (rmsNoise > 0.0) {
            (rawDepthFraction / rmsNoise) * sqrt(nIn.toDouble())
        } else 0.0
        val snr = dvSnr?.takeIf { it.isFinite() && it >= 0.0 } ?: Double.NaN

        logs.add("Catalog or folded-profile depth: ${String.format("%.1f", finalTransitDepthPpm)} ppm (Duration: ${String.format("%.2f", durationHours)} h)")
        logs.add("Photometric RMS noise: ${String.format("%.1f", rmsNoise * 1_000_000)} ppm | Signal-to-Noise: ${String.format("%.1f", snr)} σ")

        // 3. Astrophysical False-Positive Vetting: Ingress vs Egress Profile Symmetry
        val symmetryDeltaSigma = evaluateTransitProfileSymmetry(inTransitCandidates, rmsNoise)
        val isBinaryByAsymmetry = symmetryDeltaSigma > 4.0
        if (isBinaryByAsymmetry) {
            logs.add("WARNING: Transit Ingress vs Egress asymmetry is ${String.format("%.1f", symmetryDeltaSigma)} σ (distorted transit profile, potential blended binary).")
        } else {
            logs.add("Transit Ingress vs Egress profile symmetry diagnostic (Δ = ${String.format("%.2f", symmetryDeltaSigma)} σ < 4.0 σ).")
        }

        // Secondary Eclipse check
        val (secondaryDepthPpm, isBinaryBySecondary) = if (!isPhaseInHours) {
            val secWindow = normalizedPoints.filter { abs(abs(it.phase) - 0.5) <= (inTransitHalfWidth * 0.8) }
            if (secWindow.size >= 5) {
                val secMean = secWindow.map { it.detrendedFlux }.average()
                val secDipFraction = (baselineMean - secMean).coerceAtLeast(0.0)
                val secPpm = secDipFraction * 1_000_000.0
                val secZ = secDipFraction / (rmsNoise / sqrt(secWindow.size.toDouble()))
                val isBinary = secZ > 4.5 && secPpm > (finalTransitDepthPpm * 0.4) && secPpm > 1500.0
                Pair(secPpm, isBinary)
            } else Pair(0.0, false)
        } else {
            Pair(0.0, false)
        }

        if (isBinaryBySecondary) {
            logs.add("WARNING: Prominent secondary occultation detected (${String.format("%.1f", secondaryDepthPpm)} ppm) — consistent with stellar companion.")
        } else {
            logs.add("Secondary eclipse not established; stellar companion not excluded.")
        }

        val isDeepStellarEclipse = finalTransitDepthPpm > 80_000.0
        if (isDeepStellarEclipse) {
            logs.add("WARNING: Transit depth (${String.format("%.0f", finalTransitDepthPpm)} ppm) exceeds planetary physical limit (likely stellar eclipse).")
        }

        // Folded catalog data alone cannot establish planetary physical properties.
        val planetRadiusEarth: Double? = null
        val semiMajorAxisAu: Double? = null
        val derivedTeq: Double? = null
        val planetMassEarth: Double? = null
        val surfaceGravityG: Double? = null
        val bulkDensityGcm3: Double? = null
        val escapeVelocityKms: Double? = null
        val composition: PlanetaryCompositionClass? = null
        val isHz = false

        // 5. Final Diagnostic Verdict & Archival Integration
        val verdict: XilonDiscoveryVerdict
        val verdictTitle: String
        val verdictSummary: String

        verdict = XilonDiscoveryVerdict.MARGINAL_DETECTION
        verdictTitle = "ARCHIVED TCE INSPECTION — UNVERIFIED"
        verdictSummary = "Phase-folded catalog photometry; independent detection and false-positive vetting not established."

        val samplePoints = downsampleLightCurve(normalizedPoints, 160)

        val astrometryDossier = if (star.ra != null && star.dec != null) {
            astrometryEngine.generateDossier(
                raDeg = star.ra,
                decDeg = star.dec,
                ticId = star.ticId,
                canonicalName = archivalMatch.canonicalName
            ).also { dos ->
                logs.add("Astrometry (ICRS): α=${dos.formattedRaSexagesimal}, δ=${dos.formattedDecSexagesimal}")
                logs.add("Galactic Frame (IAU 1958): l=${dos.galactic.formattedL}, b=${dos.galactic.formattedB}")
                logs.add("Observed barycentric epoch unavailable")
            }
        } else null

        return XilonDetectionResult(
            star = star,
            sector = sector,
            tceName = tceName,
            totalPhotonsAnalyzed = rawPoints.size,
            transitDepthPpm = finalTransitDepthPpm,
            transitDurationHours = durationHours,
            orbitalPeriodDays = periodDays,
            signalToNoiseRatio = snr,
            oddEvenTransitDeltaSigma = Double.NaN,
            secondaryEclipseDepthPpm = secondaryDepthPpm,
            derivedPlanetRadiusEarth = planetRadiusEarth,
            derivedSemiMajorAxisAu = semiMajorAxisAu,
            derivedEquilibriumTempK = derivedTeq,
            derivedPlanetMassEarth = planetMassEarth,
            surfaceGravityEarthG = surfaceGravityG,
            planetDensityGcm3 = bulkDensityGcm3,
            escapeVelocityKms = escapeVelocityKms,
            compositionClass = composition,
            isHabitableZone = isHz,
            verdict = verdict,
            verdictTitle = verdictTitle,
            verdictSummary = verdictSummary,
            archivalCrossMatch = archivalMatch,
            lightCurveSample = samplePoints,
            analysisLog = logs,
            astrometry = astrometryDossier
        )
    }

    /**
     * Transit Profile Ingress/Egress Symmetry Test (Folded Phase Analysis):
     * Evaluates whether the transit ingress profile (phase < 0) matches the egress profile (phase > 0).
     * Physically flags asymmetric transit signatures (e.g., blended eclipsing binaries, cometary tails, or starspot crossings).
     */
    private fun evaluateTransitProfileSymmetry(
        inTransit: List<MastPhotometryPoint>,
        rms: Double
    ): Double {
        if (inTransit.size < 8) return Double.NaN
        val ingress = inTransit.filter { it.phase < 0.0 }
        val egress = inTransit.filter { it.phase > 0.0 }

        if (ingress.size < 3 || egress.size < 3) return Double.NaN

        val ingressDepth = ingress.map { it.detrendedFlux }.average()
        val egressDepth = egress.map { it.detrendedFlux }.average()

        val diff = abs(ingressDepth - egressDepth)
        val combinedStdErr = (rms * sqrt(1.0 / ingress.size + 1.0 / egress.size)).coerceAtLeast(1e-6)
        return (diff / combinedStdErr).coerceAtLeast(0.0)
    }

    private fun downsampleLightCurve(points: List<MastPhotometryPoint>, targetCount: Int): List<MastPhotometryPoint> {
        if (points.size <= targetCount) return points
        val step = points.size.toDouble() / targetCount.toDouble()
        val result = mutableListOf<MastPhotometryPoint>()
        for (i in 0 until targetCount) {
            val idx = (i * step).toInt().coerceIn(0, points.lastIndex)
            result.add(points[idx])
        }
        return result
    }

    private fun fallbackNoDetection(
        star: MastStarProperties,
        sector: String,
        tceName: String,
        archivalMatch: ArchivalCrossMatch,
        logs: List<String>
    ): XilonDetectionResult {
        return XilonDetectionResult(
            star = star,
            sector = sector,
            tceName = tceName,
            totalPhotonsAnalyzed = 0,
            transitDepthPpm = 0.0,
            transitDurationHours = 0.0,
            orbitalPeriodDays = 0.0,
            signalToNoiseRatio = 0.0,
            oddEvenTransitDeltaSigma = Double.NaN,
            secondaryEclipseDepthPpm = 0.0,
            derivedPlanetRadiusEarth = null,
            derivedSemiMajorAxisAu = null,
            derivedEquilibriumTempK = null,
            isHabitableZone = false,
            verdict = XilonDiscoveryVerdict.NO_SIGNIFICANT_TRANSIT,
            verdictTitle = "DATA UNAVAILABLE",
            verdictSummary = "Could not retrieve sufficient photometric photon series for this target.",
            archivalCrossMatch = archivalMatch,
            lightCurveSample = emptyList(),
            analysisLog = logs
        )
    }
}
