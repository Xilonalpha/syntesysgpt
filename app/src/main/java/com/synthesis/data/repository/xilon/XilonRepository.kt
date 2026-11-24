package com.synthesis.data.repository.xilon

import com.google.gson.JsonObject
import com.synthesis.data.local.dao.XilonDiscoveryDao
import com.synthesis.data.local.entity.XilonDiscoveryEntity
import com.synthesis.data.remote.api.ExoplanetArchiveApi
import com.synthesis.data.remote.api.MastExoApi
import com.synthesis.data.remote.api.MastPortalApi
import com.synthesis.domain.model.xilon.*
import com.synthesis.engine.astrometry.AstrometryEngine
import com.synthesis.engine.validation.TessHeasarcValidationEngine
import com.synthesis.engine.xilon.XilonTransitDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XilonRepository @Inject constructor(
    private val mastExoApi: MastExoApi,
    private val mastPortalApi: MastPortalApi,
    private val exoplanetArchiveApi: ExoplanetArchiveApi,
    private val xilonTransitDetector: XilonTransitDetector,
    private val xilonDiscoveryDao: XilonDiscoveryDao,
    private val pureTicCatalogRepo: PureTicCatalogRepository,
    private val astrometryEngine: AstrometryEngine,
    private val tessHeasarcValidationEngine: TessHeasarcValidationEngine
) {

    fun getPureTicCatalog(): List<PureTicCandidate> = pureTicCatalogRepo.getPureTicCatalog()

    fun getPureTicCatalogFlow(): StateFlow<List<PureTicCandidate>> = pureTicCatalogRepo.dynamicCatalogFlow

    fun getSavedDiscoveries(): Flow<List<XilonDiscoveryEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())

    private fun sanitizeSectorForResult(result: XilonDetectionResult): String {
        val raDeg = result.star.ra ?: result.astrometry?.raDeg
        val decDeg = result.star.dec ?: result.astrometry?.decDeg
        val claimed = result.sector.ifBlank { "Sector necunoscut" }
        if (raDeg == null || decDeg == null || !raDeg.isFinite() || !decDeg.isFinite() || raDeg !in 0.0..<360.0 || decDeg !in -90.0..90.0) return "$claimed (unverified)"
        val audit = tessHeasarcValidationEngine.auditTessPointing(
            ticId = result.star.ticId,
            raDeg = raDeg,
            decDeg = decDeg,
            claimedSectorStr = claimed
        )
        return if (audit.pointingMismatchFlag && audit.suggestedCorrectSector.isNotBlank()) {
            audit.suggestedCorrectSector
        } else {
            claimed
        }
    }

    private fun formatSectorLabel(sectors: List<Int>): String {
        if (sectors.isEmpty()) return "Sector necunoscut"
        val ordered = sectors.distinct().sorted()
        if (ordered.size == 1) {
            val sectorNum = ordered.first()
            return "Sector $sectorNum (s${String.format(Locale.US, "%04d", sectorNum)})"
        }
        val sectorCodes = ordered.joinToString(" / ") { sectorNum ->
            "s${String.format(Locale.US, "%04d", sectorNum)}"
        }
        val sectorNames = ordered.joinToString(" / ") { sectorNum ->
            "$sectorNum"
        }
        return "Sector $sectorNames ($sectorCodes)"
    }

    private fun resolveFallbackSectorForTid(ticId: String, raDeg: Double?, decDeg: Double?): String {
        val rawRa = raDeg ?: return "Sector necunoscut"
        val rawDec = decDeg ?: return "Sector necunoscut"
        val ecliptic = astrometryEngine.equatorialToEcliptic(rawRa, rawDec)
        val sectors = tessHeasarcValidationEngine.calculateObservedSectors(ticId, ecliptic.lambdaDeg, ecliptic.betaDeg)
        return formatSectorLabel(sectors)
    }

    suspend fun saveDiscovery(result: XilonDetectionResult, notes: String? = null): Long = withContext(Dispatchers.IO) {
        val sanitizedSector = result.sector
        val entity = XilonDiscoveryEntity(
            ticId = result.star.ticId,
            canonicalName = result.archivalCrossMatch.canonicalName,
            sector = sanitizedSector,
            verdict = result.verdict.name,
            verdictTitle = result.verdictTitle,
            verdictSummary = result.verdictSummary,
            periodDays = result.orbitalPeriodDays,
            depthPpm = result.transitDepthPpm,
            durationHours = result.transitDurationHours,
            planetRadiusEarth = result.derivedPlanetRadiusEarth,
            planetMassEarth = result.derivedPlanetMassEarth,
            surfaceGravityG = result.surfaceGravityEarthG,
            bulkDensityGcm3 = result.planetDensityGcm3,
            equilibriumTempK = result.derivedEquilibriumTempK,
            snr = result.signalToNoiseRatio,
            isHabitableZone = result.isHabitableZone,
            archetype = result.compositionClass?.label,
            archivalStatus = result.archivalCrossMatch.status.name,
            notes = notes ?: result.verdictSummary,
            raDeg = result.star.ra ?: result.astrometry?.raDeg,
            decDeg = result.star.dec ?: result.astrometry?.decDeg,
            epochBjd = null
        )
        xilonDiscoveryDao.insertDiscovery(entity)
    }

    suspend fun deleteDiscovery(id: Int) = withContext(Dispatchers.IO) {
        xilonDiscoveryDao.deleteById(id)
    }

    fun getCuratedTargets(): List<TargetStarCandidate> = emptyList()

    /** Independent chronological FITS search; catalog/TCE ephemerides are not inputs.
     * Results remain preliminary and must not be classified as novel planets.
     */
    /** Select real TIC targets independently of the TOI catalog. Selection is not evidence of novelty. */
    suspend fun selectIndependentTicTargets(maxTmag: Double, page: Int, pageSize: Int): List<String> = withContext(Dispatchers.IO) {
        require(maxTmag.isFinite() && maxTmag in 0.0..20.0)
        require(page >= 1 && pageSize in 1..100)
        val request = JsonObject().apply {
            addProperty("service", "Mast.Catalogs.Filtered.Tic")
            addProperty("format", "json")
            addProperty("page", page)
            addProperty("pagesize", pageSize)
            add("params", JsonObject().apply {
                addProperty("columns", "ID,Tmag")
                add("filters", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("paramName", "Tmag")
                        add("values", com.google.gson.JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("min", 0.0)
                                addProperty("max", maxTmag)
                            })
                        })
                    })
                })
            })
        }
        val response = mastPortalApi.invokeService(request.toString())
        require(response.get("status")?.asString == "COMPLETE") { "MAST TIC query incomplete" }
        val rows = requireNotNull(response.getAsJsonArray("data")) { "Missing TIC rows" }
        rows.mapNotNull { element ->
            val row = element.asJsonObject
            val id = row.get("ID")?.takeUnless { it.isJsonNull }?.asString
            val mag = row.get("Tmag")?.takeUnless { it.isJsonNull }?.asDouble
            if (id != null && id.matches(Regex("[0-9]+")) && mag != null &&
                mag.isFinite() && mag in 0.0..maxTmag) id else null
        }.distinct().take(pageSize)
    }

    /** Bounded independent survey; results remain unverified and retain archived evidence. */
    suspend fun runIndependentSurveyPage(
        maxTmag: Double,
        page: Int,
        batchSize: Int,
        synchronization: com.synthesis.service.MastDataSynchronizationService,
        retryTargets: List<String> = emptyList(),
        onTargetOutcome: (String, Boolean) -> Unit = { _, _ -> },
        onSignal: suspend (com.synthesis.domain.model.exoplanet.MastSyncResult, String, String) -> Unit = { _, _, _ -> }
    ): List<com.synthesis.domain.model.exoplanet.MastSyncResult> {
        require(batchSize in 1..10)
        val targets = (retryTargets.take(3) + selectIndependentTicTargets(maxTmag, page, batchSize)).distinct()
        val results = mutableListOf<com.synthesis.domain.model.exoplanet.MastSyncResult>()
        var failedTargets = 0
        for (tic in targets) {
            val observation = runIndependentSearch(tic, synchronization).getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                failedTargets++
                onTargetOutcome(tic, false)
                continueIndependentTargetLog(tic, it)
                null
            } ?: continue
            val detection = observation.periodicDetection
            onTargetOutcome(tic, true)
            if (!detection.isDetected || !detection.bestPeriodDays.isFinite() ||
                detection.bestPeriodDays <= 0.0 || !detection.transitSnr.isFinite() ||
                !detection.transitDepthPpm.isFinite() || detection.transitDepthPpm <= 0.0 ||
                detection.transitDepthPpm >= 1_000_000.0) continue
            val experimentId = observation.experimentId ?: continue
            val comparison = compareCatalogPeriods(tic, detection.bestPeriodDays)
            val comparisonEvidence = synchronization.archiveCatalogComparison(experimentId, detection.bestPeriodDays, comparison)
            onSignal(observation, comparison, comparisonEvidence)
            results.add(observation)
        }
        android.util.Log.i("XilonSurvey", "Page $page: $failedTargets/${targets.size} analyses failed")
        return results
    }

    private fun continueIndependentTargetLog(tic: String, error: Throwable) {
        android.util.Log.w("XilonSurvey", "Independent analysis failed for TIC $tic", error)
    }

    /** Exploratory follow-up on the same archived photometry; not independent confirmation. */
    suspend fun searchAdditionalTransit(observation: com.synthesis.domain.model.exoplanet.MastSyncResult,
        minPeriodDays: Double, maxPeriodDays: Double): com.synthesis.domain.model.exoplanet.PeriodicTransitDetectionResult? =
        withContext(Dispatchers.Default) {
            require(!observation.experimentId.isNullOrBlank() && observation.products.isNotEmpty())
            com.synthesis.service.PeriodicTransitAnalysisEngine(com.synthesis.engine.exoplanet.BayesianUncertaintyEngine())
                .searchAdditionalSignal(observation.dataPoints, observation.periodicDetection, minPeriodDays, maxPeriodDays)
        }

    suspend fun runIndependentSearch(
        ticId: String,
        synchronization: com.synthesis.service.MastDataSynchronizationService
    ): Result<com.synthesis.domain.model.exoplanet.MastSyncResult> {
        val cleanTic = ticId.trim().replace(Regex("^TIC\\s*", RegexOption.IGNORE_CASE), "")
        if (!cleanTic.matches(Regex("[0-9]+"))) {
            return Result.failure(IllegalArgumentException("Invalid TIC identifier"))
        }
        val result = synchronization.syncLiveObservationData(query = cleanTic, mission = "TESS")
        val observation = result.getOrElse { return Result.failure(it) }
        if (observation.experimentId.isNullOrBlank() || observation.products.isEmpty() ||
            observation.periodicDetection.periodogram.isEmpty()) {
            return Result.failure(IllegalStateException("Independent search incomplete or evidence missing"))
        }
        return Result.success(observation)
    }

    suspend fun runAutonomousDetection(
        ticId: String,
        targetPeriodDays: Double? = null
    ): Result<XilonDetectionResult> = withContext(Dispatchers.IO) {
        try {
            val cleanTic = ticId.trim().replace("TIC", "", ignoreCase = true).trim()

            require(cleanTic.matches(Regex("[0-9]+"))) { "Invalid TIC identifier" }
            val archivalCrossMatch = crossMatchArchivalDatabases(cleanTic)
            val starProps = fetchStarProperties(cleanTic).getOrThrow()
            require(listOf(starProps.radiusSun, starProps.massSun, starProps.teff).all { it != null && it.isFinite() && it > 0 }) { "Missing stellar parameters" }

            val tcesJson = mastExoApi.getTces(cleanTic)
            val tceArray = tcesJson.getAsJsonArray("TCE")

            require(tceArray != null && tceArray.size() > 0) { "No archived TCE; FFI search not implemented" }
            val parts = tceArray[0].asString.split(":")
            require(parts.size == 2 && parts[0].isNotBlank() && parts[1].matches(Regex("TCE_[0-9]+|[0-9]+"))) { "Invalid archived TCE identifier" }
            val sectorStr = parts[0]
            val tceNum = parts[1].removePrefix("TCE_")

            val tableJson = mastExoApi.getLightCurveTable(
                ticId = cleanTic,
                tce = tceNum,
                sector = sectorStr
            )

            var dvTdur: Double? = null
            var dvTdepth: Double? = null
            var dvTsnr: Double? = null
            var dvTperiod: Double? = null

            try {
                val dvInfoJson = mastExoApi.getDvInfo(cleanTic, tceNum, sectorStr)
                val hdr = dvInfoJson.getAsJsonObject("DV Data Header")
                if (hdr != null) {
                    dvTdur = hdr.get("TDUR")?.takeIf { !it.isJsonNull }?.asDouble
                    dvTdepth = hdr.get("TDEPTH")?.takeIf { !it.isJsonNull }?.asDouble
                    dvTsnr = hdr.get("TSNR")?.takeIf { !it.isJsonNull }?.asDouble
                    dvTperiod = hdr.get("TPERIOD")?.takeIf { !it.isJsonNull }?.asDouble
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }

            val dataRows = tableJson.getAsJsonArray("data")
            val points = mutableListOf<MastPhotometryPoint>()

            if (dataRows != null) {
                for (elem in dataRows) {
                    val obj = elem.asJsonObject
                    val phase = obj.get("PHASE")?.takeIf { !it.isJsonNull }?.asDouble ?: continue
                    val detrend = obj.get("LC_DETREND")?.takeIf { !it.isJsonNull }?.asDouble
                        ?: obj.get("LC_INIT")?.takeIf { !it.isJsonNull }?.asDouble
                        ?: continue
                    val model = obj.get("MODEL_INIT")?.takeIf { !it.isJsonNull }?.asDouble

                    points.add(MastPhotometryPoint(phase = phase, detrendedFlux = detrend, modelFlux = model))
                }
            }

            require(points.size >= 20 && points.all { it.phase.isFinite() && it.detrendedFlux.isFinite() }) { "Insufficient valid MAST photometry" }
            val period = requireNotNull(dvTperiod) { "Missing archived TCE period" }
            require(period.isFinite() && period > 0 && dvTdur != null && dvTdur.isFinite() && dvTdur > 0) { "Invalid archived TCE ephemeris" }

            val result = xilonTransitDetector.analyzePhotometricTimeSeries(
                rawPoints = points,
                star = starProps,
                periodDays = period,
                sector = sectorStr,
                tceName = "TCE_$tceNum",
                archivalMatch = archivalCrossMatch,
                dvDurationHours = dvTdur,
                dvDepthPpm = dvTdepth,
                dvSnr = dvTsnr
            )

            val finalResult = result
            Result.success(finalResult)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Preliminary period-only comparison, not a novelty or identity confirmation. */
    suspend fun compareCatalogPeriods(tic: String, periodDays: Double): String = withContext(Dispatchers.IO) {
        require(tic.matches(Regex("[0-9]+")) && periodDays.isFinite() && periodDays > 0)
        try {
            val confirmed = exoplanetArchiveApi.queryConfirmedTable(
                "SELECT pl_name,tic_id,pl_orbper FROM pscomppars WHERE tic_id='TIC $tic'")
            val candidates = exoplanetArchiveApi.queryToiTable(
                "SELECT toi,tid,pl_orbper FROM toi WHERE tid=$tic")
            val periods = confirmed.map { it.plOrbPer } + candidates.map { it.plOrbPer }
            val valid = periods.filterNotNull().filter { it.isFinite() && it > 0 }
            when {
                valid.any { p -> listOf(0.5, 1.0, 2.0).any { h -> kotlin.math.abs(periodDays / (p * h) - 1.0) <= 0.02 } } ->
                    "CATALOG_PERIOD_OR_HARMONIC_MATCH"
                valid.size != periods.size -> "CATALOG_PERIODS_INCOMPLETE"
                else -> "NO_PERIOD_MATCH_IN_QUERIED_TABLES; NOVELTY_UNVERIFIED"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "CATALOG_CHECK_INCOMPLETE"
        }
    }

    suspend fun crossMatchArchivalDatabases(cleanTic: String): ArchivalCrossMatch = withContext(Dispatchers.IO) {
        require(cleanTic.matches(Regex("[0-9]+"))) { "Invalid TIC identifier" }
        try {
            val queryConfirmed = "SELECT pl_name,hostname,tic_id,disc_year,disc_facility,pl_orbper,pl_trandur,pl_trandep,pl_rade,pl_bmasse,pl_eqt FROM pscomppars WHERE tic_id='TIC $cleanTic' "
            val confirmedList = exoplanetArchiveApi.queryConfirmedTable(queryConfirmed)
            if (confirmedList.isNotEmpty()) {
                val p = confirmedList[0]
                val depthPpm = p.plTranDep?.let { it * 10000.0 }
                return@withContext ArchivalCrossMatch(
                    status = ArchivalCatalogStatus.CONFIRMED_PLANET_VALIDATION,
                    canonicalName = p.plName ?: "Confirmed Planet",
                    officialDisposition = "CONFIRMED PLANET",
                    discoveryYear = p.discYear,
                    discoveryFacility = p.discFacility,
                    literaturePeriodDays = p.plOrbPer,
                    literatureDurationHours = p.plTranDur,
                    literatureDepthPpm = depthPpm,
                    literatureRadiusEarth = p.plRadE,
                    isBenchmarkTarget = true
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }

        try {
            val queryToi = "SELECT toi,tid,tfopwg_disp,pl_orbper,pl_trandurh,pl_trandep,pl_rade,pl_eqt FROM toi WHERE tid=$cleanTic"
            val toiList = exoplanetArchiveApi.queryToiTable(queryToi)
            if (toiList.isNotEmpty()) {
                val t = toiList[0]
                return@withContext ArchivalCrossMatch(
                    status = ArchivalCatalogStatus.NASA_TOI_CANDIDATE,
                    canonicalName = "TOI-${t.toi}",
                    officialDisposition = t.tfopwgDisp ?: "UNKNOWN",
                    discoveryYear = null,
                    discoveryFacility = "TESS",
                    literaturePeriodDays = t.plOrbPer,
                    literatureDurationHours = t.plTranDurH,
                    literatureDepthPpm = t.plTranDep,
                    literatureRadiusEarth = t.plRadE,
                    isBenchmarkTarget = false
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }

        throw IllegalStateException("No verified catalog match; independent FFI search and catalog completeness checks required")
    }

    suspend fun fetchStarProperties(ticId: String): Result<MastStarProperties> = withContext(Dispatchers.IO) {
        try {
            val reqPayload = JsonObject().apply {
                addProperty("service", "Mast.Catalogs.Filtered.Tic")
                addProperty("format", "json")
                val params = JsonObject().apply {
                    addProperty("columns", "ID,ra,dec,Tmag,rad,mass,Teff,d,lum")
                    val filters = com.google.gson.JsonArray().apply {
                        val f = JsonObject().apply {
                            addProperty("paramName", "ID")
                            val v = com.google.gson.JsonArray().apply { add(ticId) }
                            add("values", v)
                        }
                        add(f)
                    }
                    add("filters", filters)
                }
                add("params", params)
            }

            val resp = mastPortalApi.invokeService(reqPayload.toString())
            val dataArray = resp.getAsJsonArray("data")
            if (dataArray != null && dataArray.size() == 1) {
                val row = dataArray[0].asJsonObject
                require(row.get("ID")?.asString == ticId) { "MAST TIC identity mismatch" }
                val star = MastStarProperties(
                    ticId = ticId,
                    tMag = row.get("Tmag")?.takeIf { !it.isJsonNull }?.asDouble,
                    teff = row.get("Teff")?.takeIf { !it.isJsonNull }?.asDouble,
                    radiusSun = row.get("rad")?.takeIf { !it.isJsonNull }?.asDouble,
                    massSun = row.get("mass")?.takeIf { !it.isJsonNull }?.asDouble,
                    distancePc = row.get("d")?.takeIf { !it.isJsonNull }?.asDouble,
                    ra = row.get("ra")?.takeIf { !it.isJsonNull }?.asDouble,
                    dec = row.get("dec")?.takeIf { !it.isJsonNull }?.asDouble,
                    luminosity = row.get("lum")?.takeIf { !it.isJsonNull }?.asDouble
                )
                Result.success(star)
            } else {
                Result.failure(Exception("Star TIC $ticId not found in MAST catalog."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
