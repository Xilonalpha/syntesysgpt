package com.synthesis.service

import com.synthesis.domain.model.exoplanet.LiveObservationSession
import com.synthesis.domain.model.exoplanet.PeriodicTransitDetectionResult
import com.synthesis.domain.model.exoplanet.PhotometricDataPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class PhotometryParseResult(
    val points: List<PhotometricDataPoint>,
    val measuredDepthPpm: Double,
    val measuredRadiusEarth: Double,
    val calculatedMidBjd: Double,
    val durationMinutes: Double,
    val snr: Double,
    val chiSquaredPerDof: Double,
    val logSummary: String,
    val periodicTransitResult: PeriodicTransitDetectionResult? = null,
    val preliminarySingleDip: SingleDipEstimate? = null
)

@Singleton
class PhotometricFileParser @Inject constructor(
    val fitsImageParser: FitsImageParser,
    val periodicTransitEngine: PeriodicTransitAnalysisEngine
) {

    /**
     * Parses real photometric time series from AstroImageJ, Siril, N.I.N.A., or standard CSV/TSV.
     * Extracts BJD/JD, Relative Flux, and Uncertainty, then executes analytical light-curve inversion.
     */
    fun parsePhotometryData(rawText: String, defaultHostStarRadiusSun: Double = Double.NaN): PhotometryParseResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.isEmpty()) {
            throw IllegalArgumentException("Fișierul este gol sau conține doar comentarii.")
        }

        val rawPoints = mutableListOf<Triple<Double, Double, Double>>() // bjd, flux, error

        // Detect delimiter
        val firstDataLine = lines.firstOrNull { it.any { c -> c.isDigit() } } ?: lines[0]
        val delimiter = when {
            firstDataLine.contains(",") -> ","
            firstDataLine.contains("\t") -> "\t"
            firstDataLine.contains(";") -> ";"
            else -> "\\s+"
        }

        // Explicit time scale and matching target/error columns are required.
        val headers = lines.first().split(Regex(delimiter)).map { it.trim().lowercase(java.util.Locale.ROOT) }
        val timeColumns = headers.indices.filter { headers[it] in setOf("bjd_tdb", "bjd-tdb") }
        val fluxColumns = headers.indices.filter { headers[it] in setOf("relative_flux", "rel_flux", "normalized_flux") || headers[it].matches(Regex("rel_flux_t[0-9]+")) }
        require(timeColumns.size == 1 && fluxColumns.size == 1) { "Exactly one BJD_TDB and relative flux column required" }
        val bjdIdx = timeColumns.single()
        val fluxIdx = fluxColumns.single()
        val fluxName = headers[fluxIdx]
        val errorNames = if (fluxName.startsWith("rel_flux_t")) setOf(fluxName.replace("rel_flux_t", "rel_flux_err_t")) else setOf("flux_error", "flux_err", "relative_flux_error", "rel_flux_err", "normalized_flux_error")
        val errorColumns = headers.indices.filter { headers[it] in errorNames }
        require(errorColumns.size == 1) { "Exactly one matching flux uncertainty column required" }
        val errIdx = errorColumns.single()

        for (i in 1 until lines.size) {
            val line = lines[i]
            val tokens = line.split(Regex(delimiter)).map { it.trim() }
            if (tokens.size < 2) continue

            try {
                val bjd = tokens.getOrNull(bjdIdx)?.toDoubleOrNull() ?: continue
                val flux = tokens.getOrNull(fluxIdx)?.toDoubleOrNull() ?: continue
                val err = tokens.getOrNull(errIdx)?.toDoubleOrNull() ?: continue

                if (!bjd.isFinite() || !flux.isFinite() || !err.isFinite() || err <= 0.0) continue
                rawPoints.add(Triple(bjd, flux, err))
            } catch (_: Exception) {
                // Ignore unparseable line
            }
        }

        if (rawPoints.size < 5) {
            throw IllegalArgumentException("S-au găsit mai puțin de 5 puncte fotometrice valide în fișier.")
        }

        // Sort chronologically
        rawPoints.sortBy { it.first }
        require(rawPoints.zipWithNext().all { (a, b) -> b.first > a.first }) { "Duplicate photometric timestamps" }

        val firstBjd = rawPoints.first().first
        val lastBjd = rawPoints.last().first
        val totalSpanDays = lastBjd - firstBjd
        val totalSpanMinutes = totalSpanDays * 1440.0

        // Compute baseline flux (average of top 25% brightest points or median)
        val sortedFluxes = rawPoints.map { it.second }.sorted()
        val baselineMedian = sortedFluxes[sortedFluxes.size / 2]
        require(baselineMedian.isFinite() && baselineMedian > 0.0) { "Flux de referință invalid" }
        val normalizedFactor = baselineMedian

        val finalPoints = rawPoints.map { (bjd, f, err) ->
            val minute = (bjd - firstBjd) * 1440.0
            val normalizedFlux = f / normalizedFactor
            val normalizedErr = err / normalizedFactor
            PhotometricDataPoint(minute, bjd, normalizedFlux, normalizedErr)
        }

        val periodicResult = periodicTransitEngine.analyzeDataPoints(finalPoints, hostStarRadiusSolar = defaultHostStarRadiusSun)
        val detected = periodicResult.isDetected
        val measuredDepthPpm = if (detected) periodicResult.transitDepthPpm else Double.NaN
        val measuredRadiusEarth = if (detected && defaultHostStarRadiusSun.isFinite() && defaultHostStarRadiusSun > 0) defaultHostStarRadiusSun * sqrt(measuredDepthPpm / 1e6) * 109.07 else Double.NaN
        val midBjd = if (detected) periodicResult.epochBjd else Double.NaN
        val durationMin = if (detected) periodicResult.transitDurationHours * 60 else Double.NaN
        val snr = if (detected) periodicResult.transitSnr else Double.NaN
        val singleDip = if (!detected) findPreliminaryDip(finalPoints) else null
        val reducedChiSq = Double.NaN // model degrees of freedom not established
        val log = "${finalPoints.size} puncte importate; proveniență neconfirmată. ${periodicResult.statusSummary}" + if (singleDip != null) " Eveniment individual preliminar; semnificație locală, necalibrată global." else ""

        return PhotometryParseResult(
            points = finalPoints,
            measuredDepthPpm = measuredDepthPpm,
            measuredRadiusEarth = measuredRadiusEarth,
            calculatedMidBjd = midBjd,
            durationMinutes = durationMin,
            snr = snr,
            chiSquaredPerDof = reducedChiSq,
            logSummary = log,
            periodicTransitResult = periodicResult,
            preliminarySingleDip = singleDip
        )
    }

    // Conservative screening: exactly one contiguous low-flux run, bracketed by observations.
    private fun findPreliminaryDip(points: List<PhotometricDataPoint>): SingleDipEstimate? {
        if (points.size < 9) return null
        val flux = points.map { it.relativeFlux }.sorted()
        val median = flux[flux.size / 2]
        val deviations = flux.map { kotlin.math.abs(it - median) }.sorted()
        val errors = points.map { it.fluxError }.sorted()
        val noise = maxOf(1.4826 * deviations[deviations.size / 2], errors[errors.size / 2])
        val low = points.indices.filter { median - points[it].relativeFlux > 3 * maxOf(noise, points[it].fluxError) }
        if (low.size < 3 || low.first() < 3 || low.last() > points.lastIndex - 3) return null
        if (low.last() - low.first() + 1 != low.size) return null
        val gaps = points.zipWithNext { a, b -> b.bjdTime - a.bjdTime }.sorted()
        val cadence = gaps[gaps.size / 2]
        if (cadence <= 0 || (low.first()-1..low.last()).any { points[it+1].bjdTime - points[it].bjdTime > 3 * cadence }) return null
        return SingleDipAnalysis.estimate(points, points[low.first()].bjdTime, points[low.last()].bjdTime)
            ?.takeIf { it.localSnr >= 7.0 }
    }

    /**
     * Extracts multi-epoch photometric aperture fluxes directly from raw binary FITS frames.
     */
    fun parseFitsSeries(
        fitsFramesBytes: List<Pair<Double, ByteArray>>, // List of (BJD, raw FITS file bytes)
        targetX: Float,
        targetY: Float,
        apRadius: Float = 5.0f,
        hostStarRadiusSun: Double = Double.NaN
    ): PhotometryParseResult {
        throw IllegalStateException("Analiza cadrelor FITS necesită calibrare și incertitudini fotometrice măsurate; valorile implicite au fost eliminate.")
    }
}
