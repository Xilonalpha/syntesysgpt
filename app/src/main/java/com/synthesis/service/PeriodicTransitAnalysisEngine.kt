package com.synthesis.service

import com.synthesis.domain.model.exoplanet.*
import com.synthesis.engine.exoplanet.BayesianUncertaintyEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Box-fitting Least Squares (BLS) Periodic Transit Detection Engine.
 *
 * Implements the Kovács, Zucker & Mazeh (2002) BLS algorithm (A&A 391, 369-377)
 * to search for periodic box-shaped or trapezoidal brightness decrements in
 * photometric time series extracted from FITS image sequences or video sensors.
 *
 * Includes:
 * 1. Robust iterative sigma-clipping and median baseline normalization
 * 2. Optimal frequency grid generation tailored to the observational baseline
 * 3. Weighted BLS power spectrum computation (Signal Residue SR)
 * 4. Signal Detection Efficiency (SDE) and Transit Signal-to-Noise Ratio (SNR)
 * 5. Phase-folding with adaptive binning
 * 6. Astrophysical False-Positive Vetting (Odd/Even depth comparison & Secondary eclipse check)
 * 7. Keplerian exoplanet physical parameter inversion (Rp, a, Teq, Habitable Zone)
 */
@Singleton
class PeriodicTransitAnalysisEngine @Inject constructor(
    private val bayesianUncertaintyEngine: BayesianUncertaintyEngine
) {

    // Physical Constants (SI & Astronomical)
    private val G = 6.67430e-11        // Gravitational constant (m^3 kg^-1 s^-2)
    private val M_SUN = 1.989e30       // Solar mass in kg
    private val R_SUN = 6.9634e8       // Solar radius in meters
    private val R_EARTH = 6.3781e6     // Earth radius in meters
    private val R_JUPITER = 7.1492e7   // Jupiter radius in meters
    private val AU_METERS = 1.4959787e11
    private val SECONDS_PER_DAY = 86400.0

    /**
     * Executes the complete BLS periodic transit search on FramePhotometrySample data
     * produced by the telescope vision / FITS ingestion pipeline.
     */
    fun analyzeSamples(
        samples: List<FramePhotometrySample>,
        minPeriodDays: Double? = null,
        maxPeriodDays: Double? = null,
        hostStarRadiusSolar: Double = Double.NaN,
        hostStarMassSolar: Double = Double.NaN,
        hostStarTeffK: Double = Double.NaN
    ): PeriodicTransitDetectionResult {
        if (samples.size < 8) {
            return fallbackNoDetection("Date insuficiente (${samples.size} cadre). Sunt necesare minim 8 cadre FITS.")
        }

        // Extract time in days, normalized flux, and flux error
        if (samples.any { !it.bjd.isFinite() || it.bjd <= 2400000.0 }) {
            return fallbackNoDetection("Referință BJD absentă sau invalidă")
        }
        val rawTime = samples.map { it.bjd }
        val rawFlux = samples.map { it.normalizedFlux }
        val rawErrors = samples.map { it.fluxError }

        return runBlsPipeline(
            times = rawTime,
            fluxes = rawFlux,
            errors = rawErrors,
            minPeriodDays = minPeriodDays,
            maxPeriodDays = maxPeriodDays,
            hostStarRadiusSolar = hostStarRadiusSolar,
            hostStarMassSolar = hostStarMassSolar,
            hostStarTeffK = hostStarTeffK
        )
    }

    /**
     * Executes the BLS periodic transit search on general PhotometricDataPoint list.
     */
    fun analyzeDataPoints(
        points: List<PhotometricDataPoint>,
        minPeriodDays: Double? = null,
        maxPeriodDays: Double? = null,
        hostStarRadiusSolar: Double = Double.NaN,
        hostStarMassSolar: Double = Double.NaN,
        hostStarTeffK: Double = Double.NaN
    ): PeriodicTransitDetectionResult {
        if (points.size < 8) {
            return fallbackNoDetection("Date insuficiente (${points.size} puncte). Sunt necesare minim 8 măsurători fotometrice.")
        }

        if (points.any { !it.bjdTime.isFinite() || it.bjdTime <= 2400000.0 }) {
            return fallbackNoDetection("Referință BJD absentă sau invalidă")
        }
        val rawTime = points.map { it.bjdTime }
        val rawFlux = points.map { it.relativeFlux }
        val rawErrors = points.map { it.fluxError }

        return runBlsPipeline(
            times = rawTime,
            fluxes = rawFlux,
            errors = rawErrors,
            minPeriodDays = minPeriodDays,
            maxPeriodDays = maxPeriodDays,
            hostStarRadiusSolar = hostStarRadiusSolar,
            hostStarMassSolar = hostStarMassSolar,
            hostStarTeffK = hostStarTeffK
        )
    }

    /** Exploratory second-signal search; masked samples are not replaced with synthetic flux. */
    fun searchAdditionalSignal(
        points: List<PhotometricDataPoint>,
        primary: PeriodicTransitDetectionResult,
        minPeriodDays: Double,
        maxPeriodDays: Double,
        hostStarRadiusSolar: Double = Double.NaN,
        hostStarMassSolar: Double = Double.NaN,
        hostStarTeffK: Double = Double.NaN
    ): PeriodicTransitDetectionResult? {
        require(minPeriodDays.isFinite() && maxPeriodDays.isFinite() && minPeriodDays > 0 && maxPeriodDays > minPeriodDays)
        val p = primary.bestPeriodDays
        val epoch = primary.epochBjd
        val duration = primary.transitDurationHours / 24.0
        if (!primary.isDetected || !p.isFinite() || p <= 0 || !epoch.isFinite() ||
            !duration.isFinite() || duration <= 0 || duration >= p / 3) return null
        val remaining = points.filter {
            val cycles = (it.bjdTime - epoch) / p
            abs(cycles - round(cycles)) * p > 0.75 * duration
        }
        if (remaining.size < 8 || remaining.size == points.size) return null
        val secondary = analyzeDataPoints(remaining, minPeriodDays, maxPeriodDays,
            hostStarRadiusSolar, hostStarMassSolar, hostStarTeffK)
        if (!secondary.isDetected || !secondary.bestPeriodDays.isFinite() || secondary.bestPeriodDays <= 0) return null
        val ratio = secondary.bestPeriodDays / p
        // Conservative harmonic rejection, not a proof of distinct planetary origin.
        if ((1..5).any { k -> abs(ratio / k - 1) <= 0.02 || abs(ratio * k - 1) <= 0.02 }) return null
        return secondary
    }

    /**
     * Core Mathematical Box-fitting Least Squares (BLS) Execution Pipeline.
     */
    fun runBlsPipeline(
        times: List<Double>,
        fluxes: List<Double>,
        errors: List<Double>,
        minPeriodDays: Double? = null,
        maxPeriodDays: Double? = null,
        hostStarRadiusSolar: Double = Double.NaN,
        hostStarMassSolar: Double = Double.NaN,
        hostStarTeffK: Double = Double.NaN,
        nPhaseBins: Int = 100,
        periodSteps: Int = 600,
        allowStellarRetry: Boolean = true,
        allowPeakRetry: Boolean = true,
        globalPowerReference: Pair<Double, Double>? = null,
        constrainDuration: Boolean = false
    ): PeriodicTransitDetectionResult {
        if (constrainDuration && (!hostStarRadiusSolar.isFinite() || hostStarRadiusSolar <= 0 ||
            !hostStarMassSolar.isFinite() || hostStarMassSolar <= 0)) {
            return fallbackNoDetection("Căutarea constrânsă necesită masă și rază stelară valide")
        }
        val n = times.size
        if (n < 8 || fluxes.size != n || errors.size != n || times.any { !it.isFinite() } || fluxes.any { !it.isFinite() } || errors.any { !it.isFinite() || it <= 0.0 } || times.distinct().size != n || nPhaseBins < 6 || periodSteps < 20) {
            return fallbackNoDetection("Număr insuficient de puncte fotometrice ($n).")
        }

        // 1. Sort temporally
        val sortedIndices = times.indices.sortedBy { times[it] }
        val tSorted = sortedIndices.map { times[it] }
        val ySorted = sortedIndices.map { fluxes[it] }
        val errSorted = sortedIndices.map { errors[it] }

        val t0 = tSorted.first()
        val tSpan = tSorted.last() - t0 // Duration of baseline in days

        // 2. Iterative Sigma-Clipping for Outliers (Clip positive flares/cosmics; preserve transit dips)
        val cleanIndices = mutableListOf<Int>()
        val medianFlux = computeMedian(ySorted)
        if (medianFlux <= 0.0 || tSpan <= 0.0) return fallbackNoDetection("Flux sau interval invalid")
        val mad = max(computeMad(ySorted, medianFlux), computeMedian(errSorted)) / medianFlux
        if (!mad.isFinite() || mad <= 0.0) return fallbackNoDetection("Zgomot nedeterminabil")

        for (i in ySorted.indices) {
            val dev = ySorted[i] - medianFlux
            val isPositiveFlare = dev > 4.5 * mad * medianFlux
            val isExtremeExtinction = dev < -0.15 // > 15% drop is non-planetary
            if (!isPositiveFlare && !isExtremeExtinction) {
                cleanIndices.add(i)
            }
        }

        if (cleanIndices.size < 8) return fallbackNoDetection("Date insuficiente după filtrare")
        val useIndices = cleanIndices
        val tClean = useIndices.map { tSorted[it] }
        val yClean = useIndices.map { ySorted[it] / medianFlux } // Normalize to baseline 1.0
        val errClean = useIndices.map { errSorted[it] / medianFlux }
        val nClean = tClean.size

        // 3. Compute weights: w_i = (1 / sigma_i^2) / sum(1 / sigma_j^2)
        val minError = errClean.minOrNull() ?: return fallbackNoDetection("Erori absente")
        val rawWeights = errClean.map { (minError / it).pow(2) }
        val weightSum = rawWeights.sum()
        val wNorm = rawWeights.map { it / weightSum }

        // Mean weighted flux
        val yWeightedMean = tClean.indices.sumOf { wNorm[it] * yClean[it] }

        // 4. Period Grid Determination
        // Default lower bound must not exclude short periods merely because the observation is longer.
        val pMin = minPeriodDays?.coerceAtLeast(0.02) ?: (tSpan * 0.05).coerceIn(0.05, 0.25)
        val pMax = min(maxPeriodDays ?: 30.0, tSpan / 2.0)
        if (!pMin.isFinite() || !pMax.isFinite() || pMax <= pMin) return fallbackNoDetection("Interval insuficient pentru căutare periodică")

        val fMin = 1.0 / pMax
        val fMax = 1.0 / pMin
        // Limit accumulated phase drift to half the narrowest representable box.
        // Fail explicitly rather than silently undersample a long baseline.
        val minDuty = max(0.01, 1.0 / nPhaseBins)
        val requiredSteps = ceil((fMax - fMin) * tSpan * 2.0 / minDuty)
        val maxGridSteps = min(100000, 200000000 / nClean)
        val gridSteps = max(periodSteps.toDouble(), requiredSteps)
        if (!gridSteps.isFinite() || gridSteps > maxGridSteps)
            return fallbackNoDetection("Rezoluție BLS necesară peste buget: $gridSteps pași, limită $maxGridSteps. Restrânge intervalul de perioade.")
        val scanSteps = gridSteps.toInt()
        val freqStep = (fMax - fMin) / scanSteps

        // Trial duty cycles (duration fractions q = tau / P)
        val trialQList = listOf(0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08, 0.10, 0.12)

        val periodogram = mutableListOf<BlsPeriodogramPoint>()
        var bestPower = -1.0
        var bestPeriod = pMin
        var bestEpoch = t0
        var bestDepthFraction = 0.0
        var bestDurationDays = 0.05
        var bestQ = 0.03

        // 5. BLS Periodogram Scanning Loop
        val prefixS = DoubleArray(2 * nPhaseBins + 1)
        val prefixR = DoubleArray(2 * nPhaseBins + 1)
        val binS = DoubleArray(nPhaseBins)
        val binR = DoubleArray(nPhaseBins)
        val relativeTimes = DoubleArray(nClean) { tClean[it] - t0 }
        val weightedResiduals = DoubleArray(nClean) { wNorm[it] * (yClean[it] - yWeightedMean) }
        for (step in 0..scanSteps) {
            val freq = fMin + (step * freqStep)
            val period = 1.0 / freq

            // Bin data into phase bins [0, 1)
            binS.fill(0.0)
            binR.fill(0.0)

            for (i in 0 until nClean) {
                val phase = (relativeTimes[i] * freq).let { it - floor(it) } // [0, 1)
                val binIdx = (phase * nPhaseBins).toInt().coerceIn(0, nPhaseBins - 1)
                binS[binIdx] += weightedResiduals[i]
                binR[binIdx] += wNorm[i]
            }

            var localBestPower = 0.0
            var localBestDepth = 0.0
            var localBestQ = 0.03
            var localBestPhaseCenter = 0.0

            // Circular prefix sums: each box evaluation now takes constant time.
            for (b in 0 until 2 * nPhaseBins) {
                prefixS[b + 1] = prefixS[b] + binS[b % nPhaseBins]
                prefixR[b + 1] = prefixR[b] + binR[b % nPhaseBins]
            }
            // Exploratory envelope: radius ratio <= 0.4, duration <= twice central circular duration.
            val orbitRadius = (G * hostStarMassSolar * M_SUN * (period * SECONDS_PER_DAY).pow(2) / (4 * PI * PI)).pow(1.0 / 3.0)
            val chord = 1.4 * hostStarRadiusSolar * R_SUN / orbitRadius
            val maxDuty = if (constrainDuration && chord in 0.0..1.0) 2.0 * asin(chord) / PI else 1.0
            for (q in trialQList) {
                val windowBins = (q * nPhaseBins).roundToInt().coerceIn(1, nPhaseBins / 3)

                if (windowBins.toDouble() / nPhaseBins > maxDuty) continue
                for (startBin in 0 until nPhaseBins) {
                    val endBin = startBin + windowBins
                    val sSum = prefixS[endBin] - prefixS[startBin]
                    val rSum = prefixR[endBin] - prefixR[startBin]

                    // A transit is a BRIGHTNESS DROP: sSum must be NEGATIVE
                    if (rSum in 0.001..0.999 && sSum < 0.0) {
                        val power = (sSum * sSum) / (rSum * (1.0 - rSum))
                        if (power > localBestPower) {
                            localBestPower = power
                            localBestDepth = (-sSum) / (rSum * (1.0 - rSum))
                            localBestQ = windowBins.toDouble() / nPhaseBins
                            val centerBin = (startBin + windowBins / 2.0) % nPhaseBins
                            localBestPhaseCenter = centerBin / nPhaseBins.toDouble()
                        }
                    }
                }
            }

            // Approximate SNR at this period
            val snrAtPeriod = (localBestDepth / mad) * sqrt((localBestQ * nClean).coerceAtLeast(1.0))

            val pPoint = BlsPeriodogramPoint(
                periodDays = period,
                periodHours = period * 24.0,
                power = localBestPower,
                depthPpm = localBestDepth * 1_000_000.0,
                durationHours = (period * localBestQ) * 24.0,
                snr = snrAtPeriod
            )
            periodogram.add(pPoint)

            if (localBestPower > bestPower) {
                bestPower = localBestPower
                bestPeriod = period
                bestDepthFraction = localBestDepth
                bestQ = localBestQ
                bestDurationDays = period * localBestQ
                bestEpoch = t0 + (localBestPhaseCenter * period)
            }
        }

        // 6. Signal Detection Efficiency (SDE)
        val powers = periodogram.map { it.power }
        val meanPower = powers.average()
        val stdPower = sqrt(powers.map { (it - meanPower).pow(2) }.average())
        if (!stdPower.isFinite() || stdPower <= 0.0) return fallbackNoDetection("Periodogramă fără variație")
        val sde = globalPowerReference?.let { (bestPower - it.first) / it.second }
            ?: ((bestPower - meanPower) / stdPower)

        // 7. Phase Folding at Best Period and Epoch
        val phaseFoldedPoints = mutableListOf<PhaseFoldedPoint>()
        val inTransitHalfWidth = (bestQ * 0.5)

        for (i in 0 until nClean) {
            val t = tClean[i]
            val cycle = floor((t - bestEpoch) / bestPeriod)
            var phase = ((t - bestEpoch) / bestPeriod) - cycle // [0, 1)
            if (phase > 0.5) phase -= 1.0 // [-0.5, 0.5)

            val isInTransit = abs(phase) <= inTransitHalfWidth

            phaseFoldedPoints.add(
                PhaseFoldedPoint(
                    phase = phase,
                    relativeFlux = yClean[i],
                    fluxError = errClean[i],
                    timeMinutes = (t - t0) * 1440.0,
                    bjd = t,
                    epochIndex = round((t - bestEpoch) / bestPeriod).toLong(),
                    isInTransit = isInTransit
                )
            )
        }
        phaseFoldedPoints.sortBy { it.phase }

        // 8. Binned Phase Curve (50 bins across [-0.5, 0.5])
        val binnedCurve = computePhaseBins(phaseFoldedPoints, binCount = 40)

        // 9. Generate Trapezoid Transit Model Curve
        val modelCurve = generateTransitModelCurve(
            depthFraction = bestDepthFraction,
            halfWidthPhase = inTransitHalfWidth,
            ingressFraction = 0.15
        )

        // 10. Odd-Even Depth Vetting (Binary Star Discrimination)
        val oddTransits = phaseFoldedPoints.filter { it.isInTransit && (it.epochIndex % 2L != 0L) }
        val evenTransits = phaseFoldedPoints.filter { it.isInTransit && (it.epochIndex % 2L == 0L) }
        val outOfTransit = phaseFoldedPoints.filter { !it.isInTransit }

        val baselineMean = if (outOfTransit.isNotEmpty()) outOfTransit.map { it.relativeFlux }.average() else 1.0
        val oddDepth = if (oddTransits.isNotEmpty()) (baselineMean - oddTransits.map { it.relativeFlux }.average()).coerceAtLeast(0.0) else bestDepthFraction
        val evenDepth = if (evenTransits.isNotEmpty()) (baselineMean - evenTransits.map { it.relativeFlux }.average()).coerceAtLeast(0.0) else bestDepthFraction

        // Preserve the white-noise floor, but account for scatter between transit events.
        // Weighted cluster residuals correspond to the existing point-weighted depth.
        fun parityError(points: List<PhaseFoldedPoint>): Double {
            if (points.isEmpty()) return mad
            val white = mad / sqrt(points.size.toDouble())
            val groups = points.groupBy { it.epochIndex }.values
            if (groups.size < 2) return mad
            val mean = points.map { it.relativeFlux }.average()
            val clusterVariance = groups.sumOf { group ->
                val residual = group.sumOf { it.relativeFlux - mean }
                residual * residual
            } * groups.size.toDouble() / (groups.size - 1) /
                points.size.toDouble().pow(2)
            return max(white, sqrt(clusterVariance))
        }
        val oddErr = parityError(oddTransits)
        val evenErr = parityError(evenTransits)
        val oddEvenDiffSigma = abs(oddDepth - evenDepth) / sqrt(oddErr * oddErr + evenErr * evenErr).coerceAtLeast(1e-6)
        val isOddEvenConsistent = oddTransits.isNotEmpty() && evenTransits.isNotEmpty() && oddEvenDiffSigma < 3.0 // Consistency alone cannot distinguish planets from binaries

        // 11. Secondary Eclipse Vetting (Searches for companion occultation at phase 0.5)
        val secEclipseWindow = phaseFoldedPoints.filter { abs(abs(it.phase) - 0.5) < (inTransitHalfWidth * 1.2) }
        val secondaryBaseline = outOfTransit.filter { abs(abs(it.phase) - 0.5) >= inTransitHalfWidth * 1.2 }
        val secDepth = if (secEclipseWindow.isNotEmpty() && secondaryBaseline.isNotEmpty()) {
            (secondaryBaseline.map { it.relativeFlux }.average() - secEclipseWindow.map { it.relativeFlux }.average()).coerceAtLeast(0.0)
        } else 0.0
        val secErr = mad * sqrt(1.0 / secEclipseWindow.size.coerceAtLeast(1) + 1.0 / secondaryBaseline.size.coerceAtLeast(1))
        val secSigma = secDepth / secErr.coerceAtLeast(1e-6)
        val isSecDetected = secSigma >= 3.0 && (secDepth > bestDepthFraction * 0.25)

        // 12. Chi-Squared Fit Evaluation vs Flat Line
        var chiSqFlat = 0.0
        var chiSqTransit = 0.0
        for (pt in phaseFoldedPoints) {
            val err = pt.fluxError
            val resFlat = pt.relativeFlux - baselineMean
            chiSqFlat += (resFlat * resFlat) / (err * err)

            val modelVal = evaluateModelAtPhase(pt.phase, bestDepthFraction, inTransitHalfWidth, 0.15)
            val resTransit = pt.relativeFlux - modelVal
            chiSqTransit += (resTransit * resTransit) / (err * err)
        }
        val deltaChiSq = (chiSqFlat - chiSqTransit).coerceAtLeast(0.0)

        // 13. Exoplanet Physical Parameters Inversion
        val physicalParams = deriveExoplanetPhysicalParameters(
            periodDays = bestPeriod,
            depthFraction = bestDepthFraction,
            durationDays = bestDurationDays,
            hostStarRadiusSolar = hostStarRadiusSolar,
            hostStarMassSolar = hostStarMassSolar,
            hostStarTeffK = hostStarTeffK
        )

        // 14. Detection Significance & Vetting Decisions
        val depthPpm = bestDepthFraction * 1_000_000.0
        val inTransitCount = phaseFoldedPoints.count { it.isInTransit }.coerceAtLeast(1)
        val transitSnr = (bestDepthFraction / mad) * sqrt(inTransitCount.toDouble())

        // Detection Criteria:
        // SDE >= 3.8, Depth >= 300 ppm, SNR >= 3.0
        val observedEvents = phaseFoldedPoints.filter { it.isInTransit }.groupBy { it.epochIndex }.count { it.value.size >= 2 }
        // Weighted sinusoidal alternative at the selected period. Fit intercept,
        // sine and cosine via centered weighted normal equations.
        val angles = tClean.map { 2.0 * PI * (it - t0) / bestPeriod }
        val cs = angles.map { cos(it) }; val ss = angles.map { sin(it) }
        val cm = cs.indices.sumOf { wNorm[it] * cs[it] }
        val sm = ss.indices.sumOf { wNorm[it] * ss[it] }
        val cc = cs.indices.sumOf { wNorm[it] * (cs[it]-cm).pow(2) }
        val sc = cs.indices.sumOf { wNorm[it] * (cs[it]-cm) * (ss[it]-sm) }
        val vv = ss.indices.sumOf { wNorm[it] * (ss[it]-sm).pow(2) }
        val cy = cs.indices.sumOf { wNorm[it] * (cs[it]-cm) * (yClean[it]-yWeightedMean) }
        val sy = ss.indices.sumOf { wNorm[it] * (ss[it]-sm) * (yClean[it]-yWeightedMean) }
        val det = cc * vv - sc * sc
        val sinusoidComparable = det.isFinite() && det > 1e-12
        val a = if (sinusoidComparable) (cy*vv-sy*sc)/det else Double.NaN
        val b = if (sinusoidComparable) (sy*cc-cy*sc)/det else Double.NaN
        val sineLoss = cs.indices.sumOf { wNorm[it] * (yClean[it]-yWeightedMean-a*(cs[it]-cm)-b*(ss[it]-sm)).pow(2) }
        val boxWeight = phaseFoldedPoints.filter { it.isInTransit }.sumOf { (minError / it.fluxError).pow(2) } / weightSum
        val boxLoss = phaseFoldedPoints.sumOf {
            val model = yWeightedMean + bestDepthFraction * (boxWeight - if (it.isInTransit) 1.0 else 0.0)
            (minError / it.fluxError).pow(2) / weightSum * (it.relativeFlux-model).pow(2)
        }
        val shapePass = sinusoidComparable && boxLoss.isFinite() && sineLoss.isFinite() && boxLoss < sineLoss
        val binaryWarning = (oddTransits.isNotEmpty() && evenTransits.isNotEmpty() && !isOddEvenConsistent) || isSecDetected
        // Independent event contrasts: local sidebands reduce slow baseline drift.
        // Event scatter supplies a conservative floor against correlated cadence noise.
        val eventDepths = phaseFoldedPoints.groupBy { it.epochIndex }.values.mapNotNull { event ->
            val inside = event.filter { it.isInTransit }
            val before = event.filter { it.phase < -inTransitHalfWidth && it.phase >= -3 * inTransitHalfWidth }
            val after = event.filter { it.phase > inTransitHalfWidth && it.phase <= 3 * inTransitHalfWidth }
            if (inside.size < 2 || before.size < 2 || after.size < 2) null
            else (before.map { it.relativeFlux }.average() + after.map { it.relativeFlux }.average()) / 2 -
                inside.map { it.relativeFlux }.average()
        }
        val eventMean = if (eventDepths.isEmpty()) 0.0 else eventDepths.average()
        val eventSe = if (eventDepths.size >= 3) sqrt(eventDepths.sumOf {
            (it - eventMean).pow(2)
        } / (eventDepths.size - 1) / eventDepths.size) else Double.POSITIVE_INFINITY
        val eventSignificance = eventMean / max(eventSe, mad / sqrt(inTransitCount.toDouble()))
        val eventPass = eventDepths.size >= 3 && eventSignificance >= 4.0 &&
            eventDepths.count { it > 0 } >= ceil(0.75 * eventDepths.size).toInt()
        // Conditional circular-orbit diagnostic, not a universal eccentric-orbit veto.
        // Depth estimates radius ratio only for an undiluted uniform stellar disk.
        val aMeters = physicalParams.semiMajorAxisAu * AU_METERS
        val chordRatio = hostStarRadiusSolar * R_SUN * (1.0 + sqrt(bestDepthFraction)) / aMeters
        val maxTheoreticalDurationDays = if (hostStarMassSolar.isFinite() && hostStarMassSolar > 0 &&
            hostStarRadiusSolar.isFinite() && hostStarRadiusSolar > 0 && chordRatio > 0 && chordRatio < 1)
            bestPeriod / PI * asin(chordRatio) else Double.NaN
        val durationRatio = bestDurationDays / maxTheoreticalDurationDays
        val isDurationPhysical = durationRatio.isFinite() && durationRatio > 0 && durationRatio <= 1.0
        val isDetected = eventPass && !binaryWarning && observedEvents >= 3 && sde >= 6.0 && depthPpm >= 300.0 && transitSnr >= 7.0 && deltaChiSq > 0.0 && shapePass

        // Evaluate independent local maxima before giving up on a rejected global peak.
        // Retain the original full-search SDE normalization; narrow searches must
        // never manufacture a higher significance from their local background.
        if (!isDetected && allowPeakRetry) {
            val peaks = periodogram.indices.filter { i ->
                i > 0 && i < periodogram.lastIndex &&
                    powers[i] >= powers[i - 1] && powers[i] > powers[i + 1] &&
                    (powers[i] - meanPower) / stdPower >= 6.0 &&
                    abs(1.0 / periodogram[i].periodDays - 1.0 / bestPeriod) > 2 * freqStep
            }.sortedByDescending { powers[it] }.take(5)
            val harmonicIndices = listOf(bestPeriod / 2.0, bestPeriod * 2.0)
                .filter { it in pMin..pMax }
                .map { target -> periodogram.indices.minByOrNull {
                    abs(1.0 / periodogram[it].periodDays - 1.0 / target)
                }!! }
            for (i in (harmonicIndices + peaks).distinct()) {
                val lo = periodogram[min(i + 1, periodogram.lastIndex)].periodDays
                val hi = periodogram[max(i - 1, 0)].periodDays
                if (hi <= lo) continue
                val candidate = runBlsPipeline(times, fluxes, errors, lo, hi,
                    hostStarRadiusSolar, hostStarMassSolar, hostStarTeffK,
                    nPhaseBins, 20, false, false, meanPower to stdPower, constrainDuration)
                if (candidate.isDetected) {
                    return candidate.copy(periodogram = periodogram,
                        statusSummary = "Maxim BLS alternativ sau armonic; SDE raportat la grila globală = " +
                            String.format("%.2f", candidate.signalDetectionEfficiencySde) +
                            ". Semnal preliminar; natura planetară nu este confirmată.")
                }
            }
        }

        // Exploratory second pass: remove a dominant coherent sinusoid only when
        // the original search failed. Original observations remain untouched.
        if (!isDetected && !binaryWarning && allowStellarRetry) {
            var bestLoss = Double.POSITIVE_INFINITY
            var trend: DoubleArray? = null
            val minErr = errSorted.minOrNull()!!
            val weights = DoubleArray(n) { (minErr / errSorted[it]).pow(2) }
            val total = weights.sum()
            val ym = ySorted.indices.sumOf { weights[it]*ySorted[it] } / total
            val baseline = ySorted.indices.sumOf { weights[it]*(ySorted[it]-ym).pow(2) }
            val c = DoubleArray(n)
            val z = DoubleArray(n)
            for (step in 0..scanSteps) {
                val f = fMin + step * freqStep
                var cm = 0.0; var zm = 0.0
                for (i in 0 until n) {
                    val angle = 2 * PI * (tSorted[i]-t0)*f
                    c[i] = cos(angle); z[i] = sin(angle)
                    cm += weights[i]*c[i]; zm += weights[i]*z[i]
                }
                cm /= total; zm /= total
                var cc = 0.0; var zz = 0.0; var cz = 0.0; var cy = 0.0; var zy = 0.0
                for (i in 0 until n) {
                    val x = c[i]-cm; val v = z[i]-zm; val y = ySorted[i]-ym; val w = weights[i]
                    cc += w*x*x; zz += w*v*v; cz += w*x*v; cy += w*x*y; zy += w*v*y
                }
                val d = cc*zz-cz*cz
                if (!d.isFinite() || d <= 1e-12) continue
                val aa = (cy*zz-zy*cz)/d; val bb = (zy*cc-cy*cz)/d
                val loss = baseline-aa*cy-bb*zy
                if (loss.isFinite() && loss < bestLoss) {
                    bestLoss = loss
                    trend = DoubleArray(n) { aa*(c[it]-cm)+bb*(z[it]-zm) }
                }
            }
            // Exploratory coherence gate, not a calibrated false-alarm probability.
            if (baseline <= 0.0 || bestLoss > 0.8*baseline) trend = null
            trend?.let { fit ->
                val corrected = ySorted.indices.map { ySorted[it]-fit[it] }
                val retry = runBlsPipeline(tSorted, corrected, errSorted, minPeriodDays, maxPeriodDays,
                    hostStarRadiusSolar, hostStarMassSolar, hostStarTeffK, nPhaseBins, periodSteps, false, constrainDuration = constrainDuration)
                if (retry.isDetected) return retry.copy(statusSummary = "Semnal preliminar după eliminarea unei sinusoide; necesită verificare pe date originale. " + retry.statusSummary)
            }
        }



        val depthConsistencyNormalized = (if (oddEvenDiffSigma <= 1.0) {
            1.0
        } else {
            max(0.1, 1.0 - (oddEvenDiffSigma - 1.0) / 4.0)
        }) * (if (isSecDetected) 0.3 else 1.0)

        val vettingMetrics = TransitVettingMetrics(
            oddTransitDepthPpm = oddDepth * 1_000_000.0,
            evenTransitDepthPpm = evenDepth * 1_000_000.0,
            oddEvenSigmaDiff = oddEvenDiffSigma,
            isOddEvenConsistent = isOddEvenConsistent,
            secondaryEclipseDepthPpm = secDepth * 1_000_000.0,
            secondaryEclipseSigma = secSigma,
            isSecondaryEclipseDetected = isSecDetected,
            durationKeplerianRatio = durationRatio,
            isDurationPhysical = isDurationPhysical,
            chiSquaredFlat = chiSqFlat / nClean,
            chiSquaredTransit = chiSqTransit / nClean,
            deltaChiSquared = deltaChiSq,
            confidenceScore = 0.0,
            bayesianUncertainty = null
        )

        val status = buildString {
            if (constrainDuration) append("Căutare experimentală cu durate constrânse; FAP necalibrată. ")
            if (!durationRatio.isFinite()) append("Geometrie circulară neevaluată: parametri stelari lipsă sau incompatibili. ")
            else if (!isDurationPhysical) append("Durată peste limita modelului circular central; necesită verificarea excentricității, diluției și parametrilor stelari. ")

            if (isDetected) {
                append("★ SEMNAL PERIODIC PRELIMINAR [BLS SDE = ${String.format("%.2f", sde)}, SNR = ${String.format("%.1f", transitSnr)}]. ")
                append("P = ${String.format("%.3f", bestPeriod)} d (${String.format("%.1f", bestPeriod * 24.0)} h), ")
                append("Adâncime = ${String.format("%.0f", depthPpm)} ppm. ")
                if (!isOddEvenConsistent) {
                    append("⚠️ Alertă Binară: Discrepanță adâncime Impar/Par (${String.format("%.1f", oddEvenDiffSigma)}σ). ")
                } else if (isSecDetected) {
                    append("⚠️ Alertă Binară: Eclipsă secundară detectată la faza 0.5. ")
                } else {
                    append("Semnal preliminar; natura planetară nu este confirmată.")
                }
            } else if (binaryWarning) {
                append("Semnal periodic cu indicatori de eclipsă binară; candidat planetar neacceptat. ")
            } else if (!eventPass) {
                append("Contrast local insuficient între evenimente: n=${eventDepths.size}, statistică=${String.format("%.2f", eventSignificance)}. ")
            } else if (!shapePass) {
                append("Modelul de tranzit nu depășește alternativa sinusoidală sau comparația este nedeterminată.")
            } else {
                append("Niciun semnal periodic semnificativ peste pragul SDE (SDE = ${String.format("%.2f", sde)}; prag SDE 6.0, SNR = ${String.format("%.1f", transitSnr)}).")
            }
        }

        return PeriodicTransitDetectionResult(
            isDetected = isDetected,
            bestPeriodDays = bestPeriod,
            bestPeriodHours = bestPeriod * 24.0,
            epochBjd = bestEpoch,
            transitDepthPpm = depthPpm,
            transitDepthFraction = bestDepthFraction,
            transitDurationHours = bestDurationDays * 24.0,
            dutyCycleQ = bestQ,
            signalDetectionEfficiencySde = sde,
            transitSnr = transitSnr,
            vetting = vettingMetrics,
            physicalParameters = physicalParams,
            periodogram = periodogram,
            phaseFoldedPoints = phaseFoldedPoints,
            binnedPhaseCurve = binnedCurve,
            transitModelCurve = modelCurve,
            statusSummary = status
        )
    }

    /**
     * Inverts transit parameters into physical orbital and planetary properties.
     */
    private fun deriveExoplanetPhysicalParameters(
        periodDays: Double,
        depthFraction: Double,
        durationDays: Double,
        hostStarRadiusSolar: Double,
        hostStarMassSolar: Double,
        hostStarTeffK: Double
    ): DerivedExoplanetParameters {
        val rStarMeters = hostStarRadiusSolar * R_SUN
        val mStarKg = hostStarMassSolar * M_SUN
        val periodSeconds = periodDays * SECONDS_PER_DAY

        // 1. Planet Radius: Rp = R* * sqrt(depth)
        val radiusRatio = sqrt(depthFraction.coerceAtLeast(1e-8))
        val planetRadiusMeters = rStarMeters * radiusRatio
        val planetRadiusEarth = planetRadiusMeters / R_EARTH
        val planetRadiusJupiter = planetRadiusMeters / R_JUPITER

        // 2. Semi-Major Axis via Kepler's 3rd Law:
        // a = (G * M* * P^2 / (4 * pi^2))^(1/3)
        val aMeters = ( (G * mStarKg * periodSeconds.pow(2)) / (4.0 * Math.PI.pow(2)) ).pow(1.0 / 3.0)
        val semiMajorAxisAu = aMeters / AU_METERS

        // 3. Orbital velocity: v = 2 * pi * a / P
        val vOrbitalMps = (2.0 * Math.PI * aMeters) / periodSeconds
        val vOrbitalKmS = vOrbitalMps / 1000.0

        // 4. Impact parameter b and inclination i
        val tauSeconds = durationDays * SECONDS_PER_DAY
        val chordFraction = (vOrbitalMps * tauSeconds) / (2.0 * rStarMeters)
        val bClamped = if (chordFraction < 1.0) sqrt(1.0 - chordFraction.pow(2)).coerceIn(0.0, 0.98) else 0.0
        val inclinationRad = acos((bClamped * rStarMeters / aMeters).coerceIn(0.0, 1.0))
        val inclinationDeg = Math.toDegrees(inclinationRad)

        // 5. Stellar Luminosity & Insolation
        // L / L_sun = (R / R_sun)^2 * (Teff / 5778)^4
        val luminositySolar = hostStarRadiusSolar.pow(2) * (hostStarTeffK / 5778.0).pow(4)
        val insolationEarth = luminositySolar / semiMajorAxisAu.pow(2)

        // 6. Equilibrium Temperature (assuming Bond albedo A = 0.3)
        // Teq = Teff * sqrt(R* / (2 * a)) * (1 - A)^0.25
        val rStarAu = rStarMeters / AU_METERS
        val teqK = hostStarTeffK * sqrt(rStarAu / (2.0 * semiMajorAxisAu)) * (1.0 - 0.3).pow(0.25)

        // 7. Habitable Zone criteria (Kopparapu et al. 2013)
        val isHz = insolationEarth in 0.32..1.25

        // 8. Geometric Transit Probability: p = (R* + Rp) / a
        val prob = ((rStarMeters + planetRadiusMeters) / aMeters).coerceIn(0.001, 0.5)

        // 9. Planet classification
        val classification = when {
            !planetRadiusEarth.isFinite() -> "Necunoscut"
            planetRadiusEarth < 0.8 -> "Sub-Terestră"
            planetRadiusEarth in 0.8..1.25 -> "Planeta Terestră (Dimensiune Pământ)"
            planetRadiusEarth in 1.25..2.0 -> "Super-Pământ"
            planetRadiusEarth in 2.0..4.0 -> "Sub-Neptuniană"
            planetRadiusEarth in 4.0..8.0 -> "Gigant de Gheață (Tip Neptun)"
            periodDays < 10.0 -> "Jupiter Fierbinte (Hot Jupiter)"
            else -> "Gigant Gazos (Tip Jupiter)"
        }

        return DerivedExoplanetParameters(
            planetRadiusEarth = planetRadiusEarth,
            planetRadiusJupiter = planetRadiusJupiter,
            radiusRatioRpRs = radiusRatio,
            semiMajorAxisAu = semiMajorAxisAu,
            orbitalVelocityKmS = vOrbitalKmS,
            orbitalInclinationDeg = inclinationDeg,
            impactParameterB = bClamped,
            equilibriumTempK = teqK,
            insolationSolar = insolationEarth,
            isHabitableZone = isHz,
            planetClassification = classification,
            geometricTransitProbability = prob
        )
    }

    /**
     * Bins phase-folded points into uniformly spaced intervals across [-0.5, +0.5].
     */
    private fun computePhaseBins(
        points: List<PhaseFoldedPoint>,
        binCount: Int = 40
    ): List<PhaseBinPoint> {
        val binWidth = 1.0 / binCount
        val bins = mutableListOf<PhaseBinPoint>()

        for (b in 0 until binCount) {
            val phaseStart = -0.5 + (b * binWidth)
            val phaseEnd = phaseStart + binWidth
            val phaseCenter = phaseStart + (binWidth * 0.5)

            val inBin = points.filter { it.phase in phaseStart..phaseEnd }
            if (inBin.isNotEmpty()) {
                val meanFlux = inBin.map { it.relativeFlux }.average()
                val variance = inBin.map { (it.relativeFlux - meanFlux).pow(2) }.average()
                val error = (sqrt(variance) / sqrt(inBin.size.toDouble())).coerceAtLeast(0.0002)

                bins.add(
                    PhaseBinPoint(
                        phaseCenter = phaseCenter,
                        meanFlux = meanFlux,
                        fluxError = error,
                        pointCount = inBin.size
                    )
                )
            }
        }
        return bins
    }

    /**
     * Generates a 100-point trapezoidal/limb-darkened transit model curve over phase [-0.5, 0.5].
     */
    private fun generateTransitModelCurve(
        depthFraction: Double,
        halfWidthPhase: Double,
        ingressFraction: Double = 0.15
    ): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        val step = 0.01 // 100 points
        for (i in -50..50) {
            val phase = i * step
            val modelFlux = evaluateModelAtPhase(phase, depthFraction, halfWidthPhase, ingressFraction)
            points.add(phase to modelFlux)
        }
        return points
    }

    private fun evaluateModelAtPhase(
        phase: Double,
        depthFraction: Double,
        halfWidthPhase: Double,
        ingressFraction: Double = 0.15
    ): Double {
        val absP = abs(phase)
        val fullInWidth = halfWidthPhase * (1.0 - ingressFraction)

        return when {
            absP <= fullInWidth -> 1.0 - depthFraction
            absP <= halfWidthPhase -> {
                // Linear ingress / egress ramp
                val ramp = (halfWidthPhase - absP) / (halfWidthPhase - fullInWidth).coerceAtLeast(1e-5)
                1.0 - (depthFraction * ramp)
            }
            else -> 1.0
        }
    }

    private fun computeMedian(values: List<Double>): Double {
        if (values.isEmpty()) return 1.0
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
    }

    private fun computeMad(values: List<Double>, median: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val deviations = values.map { abs(it - median) }
        val madRaw = computeMedian(deviations)
        return 1.482602218505602 * madRaw // Normal distribution consistent scale
    }

    /**
     * Exploratory sequential search. Accepted signals are not confirmed planets.
     * Masks only accepted transit windows; never masks a rejected binary candidate.
     * Original samples and their uncertainties remain unchanged.
     */
    fun searchMultipleSignals(
        times: List<Double>, fluxes: List<Double>, errors: List<Double>,
        minPeriodDays: Double = 0.5, maxPeriodDays: Double = 10.0,
        maxSignals: Int = 3
    ): List<PeriodicTransitDetectionResult> {
        require(maxSignals in 1..5)
        require(minPeriodDays.isFinite() && maxPeriodDays.isFinite() &&
            minPeriodDays >= 0.02 && maxPeriodDays > minPeriodDays)
        require(times.size == fluxes.size && times.size == errors.size)
        require(times.all { it.isFinite() } && times.distinct().size == times.size)
        require(fluxes.all { it.isFinite() } && errors.all { it.isFinite() && it > 0 })
        var remaining = times.indices.toList()
        val results = mutableListOf<PeriodicTransitDetectionResult>()
        repeat(maxSignals) {
            if (remaining.size < 8) return results.toList()
            val result = runBlsPipeline(remaining.map { times[it] },
                remaining.map { fluxes[it] }, remaining.map { errors[it] },
                minPeriodDays, maxPeriodDays)
            if (!result.isDetected) return results.toList()
            val period = result.bestPeriodDays
            val epoch = result.epochBjd
            val halfWindow = result.transitDurationHours / 24.0 * 0.75
            if (!period.isFinite() || period <= 0 || !epoch.isFinite() ||
                !halfWindow.isFinite() || halfWindow <= 0 || halfWindow >= period / 2)
                return results.toList()
            // Stop rather than counting a repeated period or a simple harmonic as a new planet.
            if (results.any { previous ->
                listOf(0.5, 1.0, 2.0).any { ratio ->
                    abs(period / (previous.bestPeriodDays * ratio) - 1.0) < 0.02
                }
            }) return results.toList()
            val next = remaining.filter { index ->
                val cycles = (times[index] - epoch) / period
                abs(cycles - round(cycles)) * period > halfWindow
            }
            if (next.size == remaining.size) return results.toList()
            results.add(result)
            remaining = next
        }
        return results.toList()
    }

    private fun fallbackNoDetection(message: String): PeriodicTransitDetectionResult {
        val dummyVetting = TransitVettingMetrics(
            oddTransitDepthPpm = 0.0,
            evenTransitDepthPpm = 0.0,
            oddEvenSigmaDiff = 0.0,
            isOddEvenConsistent = false,
            secondaryEclipseDepthPpm = 0.0,
            secondaryEclipseSigma = 0.0,
            isSecondaryEclipseDetected = false,
            durationKeplerianRatio = 0.0,
            isDurationPhysical = false,
            chiSquaredFlat = Double.NaN,
            chiSquaredTransit = Double.NaN,
            deltaChiSquared = 0.0
        )
        val dummyParams = DerivedExoplanetParameters(
            planetRadiusEarth = 0.0,
            planetRadiusJupiter = 0.0,
            radiusRatioRpRs = 0.0,
            semiMajorAxisAu = 0.0,
            orbitalVelocityKmS = 0.0,
            orbitalInclinationDeg = 90.0,
            impactParameterB = 0.0,
            equilibriumTempK = 0.0,
            insolationSolar = 0.0,
            isHabitableZone = false,
            planetClassification = "Nedeterminat",
            geometricTransitProbability = 0.0
        )
        return PeriodicTransitDetectionResult(
            isDetected = false,
            bestPeriodDays = 0.0,
            bestPeriodHours = 0.0,
            epochBjd = 0.0,
            transitDepthPpm = 0.0,
            transitDepthFraction = 0.0,
            transitDurationHours = 0.0,
            dutyCycleQ = 0.0,
            signalDetectionEfficiencySde = 0.0,
            transitSnr = 0.0,
            vetting = dummyVetting,
            physicalParameters = dummyParams,
            periodogram = emptyList(),
            phaseFoldedPoints = emptyList(),
            binnedPhaseCurve = emptyList(),
            transitModelCurve = emptyList(),
            statusSummary = message
        )
    }
}
