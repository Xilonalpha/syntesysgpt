package com.synthesis.service

import com.synthesis.domain.model.exoplanet.DetectedStar
import com.synthesis.domain.model.exoplanet.FramePhotometrySample
import com.synthesis.domain.model.exoplanet.PeriodicTransitDetectionResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Astronomical Computer Vision & Aperture Photometry Engine.
 * Implements real sub-pixel star centroiding (intensity 1st moments),
 * FWHM seeing measurement (intensity 2nd moments), circular aperture integration
 * with annular sky background subtraction, frame-to-frame star tracking,
 * differential light curve extraction, and periodic transit parameter inversion (BLS).
 */
@Singleton
class TelescopeVisionPhotometryEngine @Inject constructor(
    private val periodicEngine: PeriodicTransitAnalysisEngine
) {

    /**
     * Analyzes a 2D float luminance buffer (e.g. from Camera2 or raw FITS),
     * discovers stellar point sources, and computes centroids with sub-pixel precision.
     */
    fun detectStars(
        pixels: FloatArray,
        width: Int,
        height: Int,
        sigmaThreshold: Float = 3.5f,
        maxStars: Int = 30,
        gainElectronsPerAdu: Double? = null,
        saturationAdu: Double? = null
    ): List<DetectedStar> {
        if (width < 41 || height < 41 || width.toLong() * height > pixels.size || maxStars <= 0) return emptyList()

        // 1. Estimate background median & sigma using sampling (every 8th pixel for real-time speed)
        val sampleStep = 8
        val sampleSize = ((width - 1) / sampleStep + 1) * ((height - 1) / sampleStep + 1)
        val sampledPixels = FloatArray(sampleSize)
        var sampleCount = 0
        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                if (sampleCount < sampleSize) {
                    sampledPixels[sampleCount++] = pixels[y * width + x]
                }
            }
        }
        sampledPixels.sort()
        val bgMedian = if (sampleCount > 0) sampledPixels[sampleCount / 2] else 0f
        // MAD (Median Absolute Deviation) for robust noise sigma: 1.4826 * median(|x - median|)
        val q25 = sampledPixels[sampleCount / 4]
        val q75 = sampledPixels[(sampleCount * 3) / 4]
        val bgSigma = ((q75 - q25) / 1.349f).coerceAtLeast(1.0f)
        val detectionThreshold = bgMedian + (sigmaThreshold * bgSigma)

        // 2. Scan for local intensity maxima (peaks)
        val candidateStars = mutableListOf<DetectedStar>()
        val margin = 21
        val searchStep = 1

        for (y in margin until height - margin step searchStep) {
            for (x in margin until width - margin step searchStep) {
                val idx = y * width + x
                val centerVal = pixels[idx]
                if (centerVal > detectionThreshold) {
                    // Check if local 3x3 maximum
                    var isPeak = true
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            if (pixels[(y + dy) * width + (x + dx)] >= centerVal) {
                                isPeak = false
                                break
                            }
                        }
                        if (!isPeak) break
                    }

                    if (isPeak) {
                        // Compute sub-pixel centroid & FWHM in an 11x11 window
                        val star = computeStarDetails(
                            id = candidateStars.size + 1,
                            peakX = x,
                            peakY = y,
                            pixels = pixels,
                            width = width,
                            height = height,
                            bgMedian = bgMedian,
                            bgSigma = bgSigma,
                            gainElectronsPerAdu = gainElectronsPerAdu,
                            saturationAdu = saturationAdu
                        )
                        if (star != null) {
                            // Avoid duplicate overlaps
                            val alreadyFound = candidateStars.any {
                                hypot(it.x - star.x, it.y - star.y) < 8.0f
                            }
                            if (!alreadyFound) {
                                candidateStars.add(star)
                            }
                        }
                    }
                }
            }
        }

        // Sort by net brightness (brightest first) and limit
        return candidateStars.sortedByDescending { it.netFlux }.take(maxStars)
    }

    /**
     * Computes the 1st intensity moments for sub-pixel centroid:
     * X_c = sum(x * (I - bg)) / sum(I - bg)
     * and 2nd moments for FWHM seeing:
     * sigma_r^2 = sum(r^2 * (I - bg)) / sum(I - bg), FWHM = 2.355 * sigma_r.
     */
    fun computeStarDetails(
        id: Int,
        peakX: Int,
        peakY: Int,
        pixels: FloatArray,
        width: Int,
        height: Int,
        bgMedian: Float,
        bgSigma: Float,
        windowRadius: Int = 5,
        apertureRadius: Float = 6.0f,
        skyInRadius: Float = 12.0f,
        skyOutRadius: Float = 20.0f,
        gainElectronsPerAdu: Double? = null,
        saturationAdu: Double? = null
    ): DetectedStar? {
        if (width <= 0 || height <= 0 || width.toLong() * height > pixels.size || peakX !in 0 until width || peakY !in 0 until height || !bgMedian.isFinite() || windowRadius < 1 || !(apertureRadius > 0 && skyInRadius > apertureRadius && skyOutRadius > skyInRadius)) return null
        var sumI = 0.0
        var sumX = 0.0
        var sumY = 0.0
        var peakAdu = 0f

        val xMin = (peakX - windowRadius).coerceAtLeast(0)
        val xMax = (peakX + windowRadius).coerceAtMost(width - 1)
        val yMin = (peakY - windowRadius).coerceAtLeast(0)
        val yMax = (peakY + windowRadius).coerceAtMost(height - 1)

        for (y in yMin..yMax) {
            for (x in xMin..xMax) {
                val valPix = pixels[y * width + x]
                if (valPix > peakAdu) peakAdu = valPix
                val weight = (valPix - bgMedian).coerceAtLeast(0f).toDouble()
                sumI += weight
                sumX += x * weight
                sumY += y * weight
            }
        }

        if (!sumI.isFinite() || sumI <= 1e-4) return null

        val subX = (sumX / sumI).toFloat()
        val subY = (sumY / sumI).toFloat()

        // 2nd moment for FWHM
        var sumR2 = 0.0
        for (y in yMin..yMax) {
            for (x in xMin..xMax) {
                val valPix = pixels[y * width + x]
                val weight = (valPix - bgMedian).coerceAtLeast(0f).toDouble()
                val dx = x - subX
                val dy = y - subY
                sumR2 += (dx * dx + dy * dy) * weight
            }
        }
        val sigmaR = sqrt(sumR2 / (2.0 * sumI))
        val fwhm = (2.35482 * sigmaR).toFloat()

        if (subX - skyOutRadius < 0 || subY - skyOutRadius < 0 || subX + skyOutRadius > width - 1 || subY + skyOutRadius > height - 1) return null
        // Circular aperture photometry with annular sky subtraction
        val apR2 = apertureRadius * apertureRadius
        val skyInR2 = skyInRadius * skyInRadius
        val skyOutR2 = skyOutRadius * skyOutRadius

        var totalApFlux = 0.0
        var apPixelCount = 0
        val skySamples = mutableListOf<Float>()

        val skyBoxRadius = skyOutRadius.toInt() + 1
        val sxMin = (subX - skyBoxRadius).toInt().coerceAtLeast(0)
        val sxMax = (subX + skyBoxRadius).toInt().coerceAtMost(width - 1)
        val syMin = (subY - skyBoxRadius).toInt().coerceAtLeast(0)
        val syMax = (subY + skyBoxRadius).toInt().coerceAtMost(height - 1)

        for (y in syMin..syMax) {
            for (x in sxMin..sxMax) {
                val dx = x - subX
                val dy = y - subY
                val dist2 = dx * dx + dy * dy
                val pix = pixels[y * width + x]

                if ((dist2 <= apR2 || dist2 in skyInR2..skyOutR2) && !pix.isFinite()) return null
                if (dist2 <= apR2) {
                    totalApFlux += pix
                    apPixelCount++
                } else if (dist2 in skyInR2..skyOutR2) {
                    skySamples.add(pix)
                }
            }
        }

        skySamples.sort()
        val skyMedian = if (skySamples.isNotEmpty()) skySamples[skySamples.size / 2].toDouble() else bgMedian.toDouble()
        val netFlux = totalApFlux - apPixelCount * skyMedian
        if (!netFlux.isFinite() || netFlux <= 0 || skySamples.size < 20) return null
        // Empirical background-only noise proxy; no assumed gain or read noise.
        val deviations = skySamples.map { abs(it - skyMedian) }.sorted()
        val sigma = 1.4826 * deviations[deviations.size / 2]
        val backgroundVariance = sigma * sigma * apPixelCount * (1.0 + Math.PI * apPixelCount / (2.0 * skySamples.size))
        if (gainElectronsPerAdu != null && (!gainElectronsPerAdu.isFinite() || gainElectronsPerAdu <= 0)) return null
        if (saturationAdu != null && (!saturationAdu.isFinite() || saturationAdu <= 0)) return null
        val noise = sqrt(backgroundVariance + (gainElectronsPerAdu?.let { netFlux / it } ?: 0.0))
        val snr = if (noise > 0) netFlux / noise else Double.NaN
        // Detector linearity is unknown; do not infer saturation from arbitrary ADU thresholds.
        val isSaturated = saturationAdu?.let { peakAdu >= it } ?: false

        return DetectedStar(
            id = id,
            x = subX,
            y = subY,
            peakAdu = peakAdu,
            fwhmPix = fwhm,
            totalFlux = totalApFlux,
            skyFluxPerPix = skyMedian,
            netFlux = netFlux,
            snr = snr,
            isSaturated = isSaturated
        )
    }

    /**
     * Tracks a previously assigned star (target or comparison) across a new frame,
     * searching within a local drift bounding box to update sub-pixel coordinates.
     */
    fun trackStarCentroid(
        previousStar: DetectedStar,
        pixels: FloatArray,
        width: Int,
        height: Int,
        searchRadiusPix: Float = 15.0f,
        apertureRadius: Float = 6.0f,
        skyInRadius: Float = 12.0f,
        skyOutRadius: Float = 20.0f,
        gainElectronsPerAdu: Double? = null,
        saturationAdu: Double? = null
    ): DetectedStar? {
        val px = previousStar.x.roundToInt()
        val py = previousStar.y.roundToInt()

        // Find local peak within search radius
        var maxVal = -Float.MAX_VALUE
        var bestX = px
        var bestY = py

        val rInt = searchRadiusPix.toInt()
        val xMin = (px - rInt).coerceAtLeast(0)
        val xMax = (px + rInt).coerceAtMost(width - 1)
        val yMin = (py - rInt).coerceAtLeast(0)
        val yMax = (py + rInt).coerceAtMost(height - 1)

        for (y in yMin..yMax) {
            for (x in xMin..xMax) {
                val dx = x - previousStar.x
                val dy = y - previousStar.y
                if (dx * dx + dy * dy <= searchRadiusPix * searchRadiusPix) {
                    val v = pixels[y * width + x]
                    if (v > maxVal) {
                        maxVal = v
                        bestX = x
                        bestY = y
                    }
                }
            }
        }

        // Recompute centroid around best peak
        val bgMedian = previousStar.skyFluxPerPix.toFloat()
        val updated = computeStarDetails(
            id = previousStar.id,
            peakX = bestX,
            peakY = bestY,
            pixels = pixels,
            width = width,
            height = height,
            bgMedian = bgMedian,
            bgSigma = 5.0f,
            apertureRadius = apertureRadius,
            skyInRadius = skyInRadius,
            skyOutRadius = skyOutRadius,
            gainElectronsPerAdu = gainElectronsPerAdu,
            saturationAdu = saturationAdu
        ) ?: return null

        return updated.copy(
            isTarget = previousStar.isTarget,
            isComparison = previousStar.isComparison
        )
    }

    /**
     * Executes real-time differential photometry:
     * Relative Flux = Target Flux / Ensemble Comparison Stars Flux.
     * Normalized relative to baseline median.
     */
    fun processFramePhotometry(
        frameIndex: Long,
        timestampMs: Long,
        timeMinutes: Double,
        bjd: Double,
        targetStar: DetectedStar,
        comparisonStars: List<DetectedStar>,
        referenceBaselineRatio: Double,
        initialTargetX: Float,
        initialTargetY: Float
    ): FramePhotometrySample? {
        if (comparisonStars.isEmpty() || !bjd.isFinite()) return null
        val stars = listOf(targetStar) + comparisonStars
        if (stars.any { it.isSaturated || !it.netFlux.isFinite() || it.netFlux <= 0 || !it.snr.isFinite() || it.snr <= 0 }) return null
        if (stars.map { it.id }.distinct().size != stars.size) return null
        val targetFlux = targetStar.netFlux
        val compFluxSum = comparisonStars.sumOf { it.netFlux }
        val rawRatio = targetFlux / compFluxSum
        val baseline = referenceBaselineRatio
        if (!baseline.isFinite() || baseline <= 0) return null
        val normalizedFlux = rawRatio / baseline
        val targetErr = 1.0 / targetStar.snr
        val compErr = sqrt(comparisonStars.sumOf { (it.netFlux / it.snr).pow(2) }) / compFluxSum
        val totalFluxError = normalizedFlux * hypot(targetErr, compErr)
        if (!totalFluxError.isFinite() || totalFluxError <= 0) return null

        val driftX = targetStar.x - initialTargetX
        val driftY = targetStar.y - initialTargetY

        return FramePhotometrySample(
            frameIndex = frameIndex,
            timestampMs = timestampMs,
            timeMinutes = timeMinutes,
            bjd = bjd,
            targetFlux = targetFlux,
            comparisonFlux = compFluxSum,
            relativeFlux = rawRatio,
            normalizedFlux = normalizedFlux,
            fluxError = totalFluxError,
            targetX = targetStar.x,
            targetY = targetStar.y,
            driftXPix = driftX,
            driftYPix = driftY,
            seeingFwhmPix = targetStar.fwhmPix,
            skyBackgroundAdu = targetStar.skyFluxPerPix
        )
    }

    /**
     * Inverts a series of photometric points to detect exoplanetary transit parameters:
     * - Depth (ppm)
     * - Planetary radius (Earth radii) assuming host star radius
     * - SNR and Chi-Squared test vs Flat Line
     */
    fun invertTransitLightCurve(
        samples: List<FramePhotometrySample>,
        hostStarRadiusSolar: Double = Double.NaN
    ): TransitInversionResult {
        if (samples.size < 5) {
            return TransitInversionResult(
                depthPpm = 0.0,
                radiusEarth = 0.0,
                snr = 0.0,
                chiSquared = 1.0,
                isDetected = false
            )
        }

        val fluxes = samples.map { it.normalizedFlux }
        val errors = samples.map { it.fluxError }

        // Find minimum 10% rolling average flux to measure transit bottom
        val windowSize = (samples.size / 6).coerceIn(3, 20)
        var minRollingFlux = 1.0
        for (i in 0..fluxes.size - windowSize) {
            val avg = fluxes.subList(i, i + windowSize).average()
            if (avg < minRollingFlux) minRollingFlux = avg
        }

        val depthRatio = (1.0 - minRollingFlux).coerceAtLeast(0.0)
        val depthPpm = depthRatio * 1_000_000.0

        // Planet radius: R_p = R_* * sqrt(depth) * (R_sun / R_earth = 109.076)
        val radiusEarth = hostStarRadiusSolar * sqrt(depthRatio) * 109.076

        // Chi-squared test vs Flat-line model (constant = 1.0)
        var chiSqFlat = 0.0
        for (i in samples.indices) {
            val residual = fluxes[i] - 1.0
            val err = errors[i].coerceAtLeast(0.0005)
            chiSqFlat += (residual * residual) / (err * err)
        }
        val reducedChiSq = chiSqFlat / samples.size

        // SNR of detection
        val meanError = errors.average().coerceAtLeast(0.0005)
        val snr = (depthRatio / meanError) * sqrt((samples.size / 3.0).coerceAtLeast(1.0))

        val isDetected = false // exploratory dip statistic, not a validated detection

        return TransitInversionResult(
            depthPpm = depthPpm,
            radiusEarth = radiusEarth,
            snr = snr,
            chiSquared = reducedChiSq,
            isDetected = isDetected
        )
    }

    /**
     * Executes the Box-fitting Least Squares (BLS) periodogram search to detect
     * periodic dips in the processed FITS or video light curve.
     */
    fun runPeriodicTransitAnalysis(
        samples: List<FramePhotometrySample>,
        minPeriodDays: Double? = null,
        maxPeriodDays: Double? = null,
        hostStarRadiusSolar: Double = Double.NaN,
        hostStarMassSolar: Double = Double.NaN,
        hostStarTeffK: Double = Double.NaN
    ): PeriodicTransitDetectionResult {
        return periodicEngine.analyzeSamples(
            samples = samples,
            minPeriodDays = minPeriodDays,
            maxPeriodDays = maxPeriodDays,
            hostStarRadiusSolar = hostStarRadiusSolar,
            hostStarMassSolar = hostStarMassSolar,
            hostStarTeffK = hostStarTeffK
        )
    }
}

data class TransitInversionResult(
    val depthPpm: Double,
    val radiusEarth: Double,
    val snr: Double,
    val chiSquared: Double,
    val isDetected: Boolean
)
