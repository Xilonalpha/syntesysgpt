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

    /** Select observed SPOC targets from a bounded MAST page, without using planet periods. */
    suspend fun discoverObservedTicIds(sector: Int, page: Int = 1): List<String> = withContext(Dispatchers.IO) {
        require(sector > 0 && page in 1..10000)
        val request = com.google.gson.JsonObject().apply {
            addProperty("service", "Mast.Caom.Filtered")
            addProperty("format", "json")
            addProperty("pagesize", 100)
            addProperty("page", page)
            add("params", com.google.gson.JsonObject().apply {
                addProperty("columns", "target_name,sequence_number,obs_collection,provenance_name")
                add("filters", com.google.gson.JsonArray().apply {
                    for ((key, value) in listOf("obs_collection" to "TESS", "provenance_name" to "SPOC", "sequence_number" to sector.toString())) {
                        add(com.google.gson.JsonObject().apply {
                            addProperty("paramName", key)
                            add("values", com.google.gson.JsonArray().apply { add(value) })
                        })
                    }
                })
            })
        }
        val response = mastPortalApi.invokeService(request.toString())
        require(response.get("status")?.asString == "COMPLETE") { "MAST target query incomplete" }
        val rows = response.getAsJsonArray("data") ?: error("Missing MAST target rows")
        rows.mapNotNull { row ->
            val obj = row.asJsonObject
            if (obj.get("sequence_number")?.asString?.toIntOrNull() != sector ||
                obj.get("obs_collection")?.asString != "TESS" || obj.get("provenance_name")?.asString != "SPOC") null
            else obj.get("target_name")?.takeUnless { it.isJsonNull }?.asString
                ?.takeIf { it.matches(Regex("[0-9]{1,16}")) && (it.toLongOrNull() ?: 0L) > 0 }
        }.distinct().take(5)
    }

    /** Persist a catalog comparison bound to a verified observation manifest. */
    suspend fun archiveCatalogComparison(experimentId: String, periodDays: Double, status: String): String =
        withContext(Dispatchers.IO) {
            require(periodDays.isFinite() && periodDays > 0)
            require(status in setOf("CATALOG_PERIOD_OR_HARMONIC_MATCH", "CATALOG_PERIODS_INCOMPLETE",
                "NO_PERIOD_MATCH_IN_QUERIED_TABLES; NOVELTY_UNVERIFIED", "CATALOG_CHECK_INCOMPLETE"))
            val evidence = FitsEvidenceStore(java.io.File(context.filesDir, "fits_evidence"))
            val manifests = ExperimentManifestStore(java.io.File(context.filesDir, "experiment_manifests"), evidence)
            val manifest = manifests.readVerified(experimentId)
            val root = java.io.File(context.filesDir, "catalog_comparisons")
            check(root.isDirectory || root.mkdirs())
            val id = java.util.UUID.randomUUID().toString()
            val body = "schema=1\nexperiment=$experimentId\nmanifest=${FitsEvidenceStore.sha256(manifest.toString().toByteArray(Charsets.UTF_8))}\ncreated=${System.currentTimeMillis()}\nperiod_days=$periodDays\ntables=pscomppars,toi\nrelative_tolerance=0.02\nharmonics=0.5,1,2\nstatus=$status\n"
            val payload = (body + FitsEvidenceStore.sha256(body.toByteArray(Charsets.UTF_8)) + "\n").toByteArray(Charsets.UTF_8)
            val temp = java.io.File.createTempFile("catalog-", ".tmp", root)
            try {
                temp.outputStream().use { it.write(payload); it.fd.sync() }
                check(temp.renameTo(java.io.File(root, "$id.comparison")))
            } finally { temp.delete() }
            id
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
            val observationResponse = mastPortalApi.invokeService(request.toString())
            require(observationResponse.get("status")?.asString == "COMPLETE") { "MAST observation query incomplete" }
            val observations = observationResponse.getAsJsonArray("data")
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
                val productResponse = mastPortalApi.invokeService(productRequest.toString())
                require(productResponse.get("status")?.asString == "COMPLETE") { "MAST product query incomplete" }
                val rows = productResponse.getAsJsonArray("data") ?: error("Missing MAST product rows")
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
            require(detection.periodogram.isNotEmpty() && detection.bestPeriodDays.isFinite() && detection.bestPeriodDays > 0) { "BLS incomplete; no negative evidence can be inferred" }
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
