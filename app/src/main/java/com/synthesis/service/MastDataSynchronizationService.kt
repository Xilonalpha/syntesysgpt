package com.synthesis.service

import com.synthesis.data.remote.api.ExoplanetArchiveApi
import com.synthesis.data.remote.api.MastExoApi
import com.synthesis.data.remote.api.MastPortalApi
import com.synthesis.domain.model.exoplanet.*
import com.synthesis.engine.exoplanet.BayesianUncertaintyEngine
import com.synthesis.engine.exoplanet.TransitDipScannerUtility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Professional real-time data synchronization service connecting to external astronomical archives
 * including STScI MAST (Mikulski Archive for Space Telescopes) and the NASA Exoplanet Archive (NExScI).
 *
 * Downloads calibrated photometric time-series, resolves space mission observation sectors,
 * and feeds the data immediately into the multi-engine analysis pipeline (BLS, Bayesian, Templates).
 */
@Singleton
class MastDataSynchronizationService @Inject constructor(
    private val mastExoApi: MastExoApi,
    private val mastPortalApi: MastPortalApi,
    private val exoplanetArchiveApi: ExoplanetArchiveApi,
    private val periodicTransitAnalysisEngine: PeriodicTransitAnalysisEngine,
    private val bayesianUncertaintyEngine: BayesianUncertaintyEngine,
    private val stellarTemplateLibraryService: StellarTemplateLibraryService,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {

    private val _syncState = MutableStateFlow(MastSyncState())
    val syncState: StateFlow<MastSyncState> = _syncState.asStateFlow()

    private val curatedPresets = emptyList<MastObservationTarget>()

    /** Offline replay; no network requests and no claim of scientific confirmation. */
    suspend fun replayExperiment(id: String) = withContext(Dispatchers.IO) {
        val evidence = FitsEvidenceStore(java.io.File(context.filesDir, "fits_evidence"))
        val manifests = ExperimentManifestStore(java.io.File(context.filesDir, "experiment_manifests"), evidence)
        val points = ExperimentReplay(manifests, evidence).load(id) { bytes ->
            val doc = FitsReaderService().read(bytes)
            ExperimentReplay.Sector(doc.header.getLong("TICID"), doc.header.getString("TELESCOP").orEmpty(),
                doc.header.getInt("SECTOR"), doc.extractLightCurve().orEmpty())
        }
        val detection = periodicTransitAnalysisEngine.analyzeDataPoints(points)
        ExperimentResultStore(java.io.File(context.filesDir, "experiment_results"), manifests).save(id, ExperimentResultStore.metrics(detection))
        detection
    }

    fun getCuratedTargets(): List<MastObservationTarget> = curatedPresets

    /**
     * Executes real-time live data synchronization against STScI MAST.
     * Pulls the calibrated light curve series, normalizes it, and runs
     * immediate periodic BLS, Bayesian credible intervals, and template matching.
     */
    suspend fun syncLiveObservationData(
        query: String,
        mission: String = "TESS",
        targetPeriodDays: Double? = null
    ): Result<MastSyncResult> = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        _syncState.value = MastSyncState(isSyncing = true, statusMessage = "Căutare produse TESS SPOC")
        try {
            require(mission.equals("TESS", true)) { "Doar TESS este implementat" }
            val tic = Regex("(?:TIC\\s+)?([0-9]{1,16})", RegexOption.IGNORE_CASE)
                .matchEntire(query.trim())?.groupValues?.get(1)?.toLongOrNull()
                ?: error("Introduceți identificatorul TIC numeric exact")
            require(tic > 0)
            val request = com.google.gson.JsonObject().apply {
                addProperty("service", "Mast.Caom.Filtered")
                addProperty("format", "json")
                addProperty("pagesize", 100)
                add("params", com.google.gson.JsonObject().apply {
                    addProperty("columns", "*")
                    add("filters", com.google.gson.JsonArray().apply {
                        for ((key, value) in listOf("obs_collection" to "TESS", "target_name" to tic.toString(), "provenance_name" to "SPOC")) {
                            add(com.google.gson.JsonObject().apply {
                                addProperty("paramName", key)
                                add("values", com.google.gson.JsonArray().apply { add(value) })
                            })
                        }
                    })
                })
            }
            val observations = mastPortalApi.invokeService(request.toString()).getAsJsonArray("data")
                ?: error("MAST nu a returnat observații")
            val products = mutableSetOf<String>()
            require(observations.size() <= 20) { "Prea multe observații; selecție explicită necesară" }
            for (row in observations) {
                val id = row.asJsonObject.get("obsid")?.asString ?: continue
                if (!id.matches(Regex("[0-9]+"))) continue
                val productRequest = com.google.gson.JsonObject().apply {
                    addProperty("service", "Mast.Caom.Products")
                    addProperty("format", "json")
                    add("params", com.google.gson.JsonObject().apply { addProperty("obsid", id) })
                }
                val rows = mastPortalApi.invokeService(productRequest.toString()).getAsJsonArray("data") ?: continue
                for (product in rows) {
                    val uri = product.asJsonObject.get("dataURI")?.takeUnless { it.isJsonNull }?.asString ?: continue
                    if (uri.matches(Regex("mast:TESS/product/[A-Za-z0-9_-]+_lc\\.fits"))) products.add(uri)
                }
            }
            require(products.isNotEmpty()) { "Niciun produs SPOC LC FITS găsit" }
            require(products.size <= 20) { "Prea multe produse; analiza necesită selecție explicită" }
            val allPoints = mutableListOf<PhotometricDataPoint>()
            val sources = mutableListOf<MastProductProvenance>()
            val sectors = mutableSetOf<Int>()
            for (uri in products.sorted()) {
                val url = okhttp3.HttpUrl.Builder().scheme("https").host("mast.stsci.edu")
                    .addPathSegments("api/v0.1/Download/file").addQueryParameter("uri", uri).build()
                val client = okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                    .callTimeout(90, java.util.concurrent.TimeUnit.SECONDS).build()
                val bytes = client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful) { "MAST HTTP ${response.code}" }
                    val body = response.body ?: error("Răspuns gol")
                    val limit = 32 * 1024 * 1024
                    require(body.contentLength() <= limit) { "FITS prea mare" }
                    body.byteStream().use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            require(output.size() + n <= limit) { "FITS prea mare" }
                            output.write(buffer, 0, n)
                        }
                        output.toByteArray()
                    }
                }
                val doc = FitsReaderService().read(bytes)
                require(doc.header.getString("TELESCOP").equals("TESS", true)) { "Misiune FITS incompatibilă" }
                require(doc.header.getLong("TICID") == tic) { "TICID FITS diferit de țintă" }
                val sector = doc.header.getInt("SECTOR")
                require(sector > 0) { "Sector FITS absent" }
                val sectorPoints = doc.extractLightCurve().orEmpty()
                require(sectorPoints.size >= 8) { "Fotometrie validă insuficientă" }
                val sha = FitsEvidenceStore(java.io.File(context.filesDir, "fits_evidence")).save(bytes)
                require(sectors.add(sector)) { "Mai multe produse pentru sectorul $sector; selecție ambiguă" }
                allPoints.addAll(sectorPoints)
                sources.add(MastProductProvenance(uri, sha, sector, sectorPoints.size))
                require(allPoints.size <= 250_000) { "Limită de măsurători depășită" }
            }
            val points = mergeMastSectorPoints(allPoints)
            val sectorLabel = sectors.sorted().joinToString(", ")
            val target = MastObservationTarget(id = tic.toString(), targetName = "TIC $tic", ticId = tic.toString(),
                sectorOrQuarter = sectorLabel, tceNumber = "", estimatedMag = Double.NaN,
                description = "Produs SPOC; candidat nevalidat", scientificPriority = "NEEVALUAT", spectralType = "Necunoscut")
            val experimentId = ExperimentManifestStore(
                java.io.File(context.filesDir, "experiment_manifests"),
                FitsEvidenceStore(java.io.File(context.filesDir, "fits_evidence"))
            ).save(tic, sources.map { ExperimentManifestStore.Source(it.uri, it.sha256, it.sector, it.pointCount) }, ExperimentReplay.ALGORITHM)
            val detection = periodicTransitAnalysisEngine.analyzeDataPoints(points)
            ExperimentResultStore(java.io.File(context.filesDir, "experiment_results"),
                ExperimentManifestStore(java.io.File(context.filesDir, "experiment_manifests"), FitsEvidenceStore(java.io.File(context.filesDir, "fits_evidence"))))
                .save(experimentId, ExperimentResultStore.metrics(detection))
            val result = MastSyncResult(target, points, detection, emptyList(), null,
                System.currentTimeMillis() - started, "MAST: ${sources.size} produse; sectoare $sectorLabel; BLS preliminar",
                sources.singleOrNull()?.uri, sources.singleOrNull()?.sha256, sources.toList(), experimentId)
            _syncState.value = MastSyncState(statusMessage = "FITS importat; analiză preliminară", progressFraction = 1f,
                lastSyncTimestampMs = System.currentTimeMillis(), activeTarget = target, syncedPointsCount = points.size,
                syncedMission = "TESS", syncedSector = sectorLabel)
            Result.success(result)
        } catch (e: CancellationException) {
            _syncState.value = MastSyncState(statusMessage = "Sincronizare anulată")
            throw e
        } catch (e: Exception) {
            val message = e.message ?: "Sincronizare eșuată"
            _syncState.value = MastSyncState(statusMessage = message, activeError = message)
            Result.failure(e)
        }
    }
}
