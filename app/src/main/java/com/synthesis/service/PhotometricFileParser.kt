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
    val periodicTransitResult: PeriodicTransitDetectionResult? = null
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

        // Header detection
        var bjdIdx = 0
        var fluxIdx = 1
        var errIdx = 2
        var startLine = 0

        val headerTokens = lines[0].split(Regex(delimiter)).map { it.trim().lowercase() }
        val hasHeader = headerTokens.any { it.contains("bjd") || it.contains("flux") || it.contains("jd") || it.contains("mag") || it.contains("time") }

        if (hasHeader) {
            startLine = 1
            headerTokens.forEachIndexed { index, token ->
                val isErr = token.contains("err") || token.contains("uncert") || token.contains("sigma")
                if (isErr) {
                    errIdx = index
                } else if (token.contains("rel_flux") || token.contains("flux") || token.contains("normalized")) {
                    fluxIdx = index
                } else if (token.contains("bjd") || token.contains("jd") || token.contains("time")) {
                    bjdIdx = index
                }
            }
        }

        for (i in startLine until lines.size) {
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
        val reducedChiSq = Double.NaN // model degrees of freedom not established
        val log = "${finalPoints.size} puncte importate; proveniență neconfirmată. ${periodicResult.statusSummary}"

        return PhotometryParseResult(
            points = finalPoints,
            measuredDepthPpm = measuredDepthPpm,
            measuredRadiusEarth = measuredRadiusEarth,
            calculatedMidBjd = midBjd,
            durationMinutes = durationMin,
            snr = snr,
            chiSquaredPerDof = reducedChiSq,
            logSummary = log,
            periodicTransitResult = periodicResult
        )
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
