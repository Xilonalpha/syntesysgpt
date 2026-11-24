package com.synthesis.service

import com.synthesis.data.remote.api.ExoplanetArchiveApi
import com.synthesis.data.repository.exoplanet.PublicSpectralDatasetRegistry
import com.synthesis.domain.model.exoplanet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Service that compares retrieved planetary spectral data against a public dataset registry
 * (NASA Exoplanet Archive, space telescope published benchmarks, and theoretical chemical baselines)
 * to identify potential unique anomalies, novel biosignatures/technosignatures, and zero-prior discoveries.
 */
@Singleton
class PlanetarySpectralAnomalyService @Inject constructor(
    val registry: PublicSpectralDatasetRegistry,
    private val exoplanetArchiveApi: ExoplanetArchiveApi
) {

    /**
     * Compares a retrieved planetary spectrum against the public dataset registry.
     *
     * @param retrievedSpectrum The planetary spectrum to evaluate.
     * @param requestedBenchmarkId Optional specific benchmark ID to compare against. If null, the best matching baseline is auto-selected.
     * @return Comprehensive comparison report with statistical significance and detected anomalies.
     */
    fun compareAgainstPublicRegistry(
        retrievedSpectrum: RetrievedPlanetarySpectrum,
        requestedBenchmarkId: String? = null
    ): PublicRegistryComparisonResult { throw UnsupportedOperationException("Verified spectral products and uncertainties required") }

    /**
     * Interrogates NASA Exoplanet Archive TAP API to determine if any peer-reviewed
     * transmission spectroscopy or confirmed planetary parameters exist for this target.
     */
    suspend fun queryNasaArchiveForTarget(targetIdentifier: String): PublicArchiveDispositionInfo = withContext(Dispatchers.IO) {
        throw UnsupportedOperationException("Archive spectral coverage has not been verified")
    }

    /**
     * Curated sample spectra ready for testing, verifying calibration, and showcasing discoveries.
     */
    fun getCuratedRetrievedSpectra(): List<RetrievedPlanetarySpectrum> { return emptyList() }

    // --- Private Helper Methods ---

    private fun findInterpolatedBaselinePoint(
        wavelength: Double,
        baselinePoints: List<SpectralDataPoint>
    ): SpectralDataPoint? {
        if (baselinePoints.isEmpty()) return null
        // Find exact match within 0.08 um
        val closeMatch = baselinePoints.firstOrNull { abs(it.wavelengthMicrons - wavelength) <= 0.08 }
        if (closeMatch != null) return closeMatch

        // Interpolate between two surrounding points
        val sorted = baselinePoints.sortedBy { it.wavelengthMicrons }
        val lower = sorted.lastOrNull { it.wavelengthMicrons <= wavelength }
        val upper = sorted.firstOrNull { it.wavelengthMicrons >= wavelength }

        return when {
            lower != null && upper != null && lower != upper -> {
                val ratio = (wavelength - lower.wavelengthMicrons) / (upper.wavelengthMicrons - lower.wavelengthMicrons)
                val interpFlux = lower.fluxOrDepthPpm + ratio * (upper.fluxOrDepthPpm - lower.fluxOrDepthPpm)
                val interpError = (lower.uncertaintyPpm + upper.uncertaintyPpm) / 2.0
                SpectralDataPoint(
                    wavelengthMicrons = wavelength,
                    fluxOrDepthPpm = interpFlux,
                    uncertaintyPpm = interpError
                )
            }
            lower != null -> lower
            upper != null -> upper
            else -> sorted.first()
        }
    }

    private fun calculateNoveltyIndex(
        isTargetKnownInRegistry: Boolean,
        maxSigma: Double,
        anomalies: List<SpectralAnomaly>,
        reducedChiSq: Double
    ): Double {
        var score = 0.0

        // 1. Zero-prior status: 35 points if target was never published before
        if (!isTargetKnownInRegistry) {
            score += 35.0
        }

        // 2. Statistical peak significance: up to 30 points
        val sigmaScore = (maxSigma / 6.0).coerceIn(0.0, 1.0) * 30.0
        score += sigmaScore

        // 3. Presence of exotic biosignature / technosignature bands: up to 25 points
        val hasExoticBand = anomalies.any {
            it.anomalyType == SpectralAnomalyType.UNPRECEDENTED_MOLECULAR_BAND ||
                    it.anomalyType == SpectralAnomalyType.ANOMALOUS_DEPTH_SURGE
        }
        if (hasExoticBand) {
            score += 25.0
        }

        // 4. Residual deviation spread: up to 10 points
        if (reducedChiSq > 2.5) {
            score += 10.0
        } else if (reducedChiSq > 1.5) {
            score += 5.0
        }

        return score.coerceIn(0.0, 100.0)
    }

    private fun buildVerdictAndRecommendations(
        targetName: String,
        isTargetKnown: Boolean,
        benchmark: RegisteredSpectralBenchmark,
        maxSigma: Double,
        anomalies: List<SpectralAnomaly>,
        noveltyIndex: Double
    ): Pair<String, List<String>> {
        val recommendations = mutableListOf<String>()

        val verdict = when {
            noveltyIndex >= 80.0 -> {
                recommendations.add("Prioritate Maximă: Propune ținta pentru observație țintită JWST DDT (Director's Discretionary Time).")
                recommendations.add("Efectuează validare prin spectrograf de înaltă rezoluție la sol (VLT CRIRES+ sau ESPRESSO) pentru confirmarea liniilor înguste.")
                recommendations.add("Trimite raportul de anomalie către arhivele de colaborare științifică ExoFOP-TESS / NASA NExScI.")
                recommendations.add("Verifică activitatea magnetică a stelei gazdă pentru a exclude efectul de contaminare a petelor stelare (stellar spot unocculted crossing).")
                "★ DESCOPERIRE POTENȚIAL UNICĂ (Indice Noutate: ${noveltyIndex.format(1)}%): Semnături spectrale fără precedent (${maxSigma.format(1)}σ) absente în registrul public NASA/MAST."
            }

            noveltyIndex >= 50.0 -> {
                recommendations.add("Verifică posibile interferențe instrumentale sau erori sistematice pe detector.")
                recommendations.add("Realizează o nouă stivă de tranzit (stacked transits) pentru a crește raportul S/N peste pragul de 5.0σ.")
                recommendations.add("Compară cu modelele radiative non-LTE pentru confirmarea compoziției chimice.")
                "ANOMALIE ATMOSFERICĂ SEMNIFICATIVĂ (${maxSigma.format(1)}σ): Deviații clare față de linia de bază ${benchmark.targetName}, indicând chimism sau dinamici neprevăzute."
            }

            noveltyIndex >= 25.0 -> {
                recommendations.add("Datele sunt compatibile cu variații statistice normale sau zgomot de fond moderat.")
                recommendations.add("Recomandat monitorizarea tranzitelor ulterioare pentru rafinarea barelor de eroare.")
                "DEVIAȚIE STATISTICĂ MODERATĂ: Semnalul este în mare măsură compatibil cu registrul public, cu mici anomalii marginale."
            }

            else -> {
                recommendations.add("Calibrare validată cu succes: Răspunsul spectral este în deplină concordanță cu arhiva publică.")
                "CONFORM CU REGISTRUL PUBLIC: Spectrul obținut confirmă cu precizie ridicată modelul de referință ${benchmark.targetName} (χ²_red = normal)."
            }
        }

        return Pair(verdict, recommendations)
    }

    private fun Double.format(digits: Int): String = String.format(java.util.Locale.US, "%.${digits}f", this)
}
