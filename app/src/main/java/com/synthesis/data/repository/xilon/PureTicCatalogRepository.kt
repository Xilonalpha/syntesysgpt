package com.synthesis.data.repository.xilon

import com.synthesis.domain.model.ExoplanetCandidate
import com.synthesis.domain.model.ExoplanetCatalogSource
import com.synthesis.domain.model.TfopwgDisposition
import com.synthesis.domain.model.xilon.MastPhotometryPoint
import com.synthesis.domain.model.xilon.PureTicCandidate
import com.synthesis.domain.model.xilon.TargetStarCandidate
import com.synthesis.data.local.dao.CachedExoplanetDao
import com.synthesis.data.local.dao.PureTicDao
import com.synthesis.data.local.entity.CachedExoplanetEntity
import com.synthesis.data.local.entity.PureTicEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random
import java.util.Locale

@Singleton
class PureTicCatalogRepository @Inject constructor(
    private val pureTicDao: PureTicDao,
    private val cachedExoplanetDao: CachedExoplanetDao,
    private val researchDao: com.synthesis.data.local.dao.SynthesizedResearchDao
) {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Legacy rows have no verifiable provenance. Keep them on disk but quarantine them.
    val dynamicCatalogFlow: StateFlow<List<PureTicCandidate>> = researchDao.getAllResearch()
        .map { rows -> rows.mapNotNull { row ->
            if (!row.id.startsWith("independent-tess:") || row.isVerified || row.sourceCount <= 0) null
            else try {
                val j = com.google.gson.JsonParser.parseString(row.synthesizedDataJson).asJsonObject
                fun number(key: String): Double = j.get(key)?.asDouble?.takeIf { it.isFinite() && it > 0.0 }
                    ?: throw IllegalArgumentException("Missing positive measurement: $key")
                fun string(key: String): String = j.get(key)?.asString?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("Missing metadata: $key")
                val tic = string("tic")
                val experiment = string("experiment_id")
                require(tic.matches(Regex("[0-9]+")) && row.id == "independent-tess:$experiment")
                require(string("catalog_status") == "NO_PERIOD_MATCH_IN_QUERIED_TABLES; NOVELTY_UNVERIFIED")
                val evidence = string("comparison_evidence")
                val depth = number("depth_ppm")
                require(depth < 1_000_000.0)
                PureTicCandidate(
                    ticId = tic, name = "TIC $tic", sector = string("sector"),
                    apparentMag = j.get("tmag").asDouble.also { require(it.isFinite()) },
                    estimatedPeriodDays = number("period_days"),
                    transitDurationHours = number("duration_hours"), transitDepthPpm = depth,
                    stellarTeffK = number("stellar_teff"), stellarRadiusSun = number("stellar_radius"),
                    stellarMassSun = number("stellar_mass"), distancePc = number("distance_pc"),
                    discoveryOrigin = "Analiza fotometrica TESS independenta",
                    whyNoToi = "Fara potrivire de perioada in tabelele interogate; noutate neconfirmata",
                    potentialPlanetaryType = "Semnal de tranzit nevalidat",
                    scientificNotes = "Experiment: $experiment; comparatie: $evidence",
                    isLiveIngested = true, ingestionTimestamp = row.timestamp, snrSigma = number("snr")
                )
            } catch (_: Exception) { null }
        }.distinctBy { it.ticId } }
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())
    fun getPureTicCatalog(): List<PureTicCandidate> = dynamicCatalogFlow.value
    fun getCandidateByTic(ticId: String): PureTicCandidate? =
        dynamicCatalogFlow.value.firstOrNull { it.ticId == ticId }

    fun toTargetStarCandidate(c: PureTicCandidate): TargetStarCandidate {
        return TargetStarCandidate(
            ticId = c.ticId,
            name = c.name,
            description = "[TIC • STATUT CATALOG NEVERIFICAT] ${c.discoveryOrigin}. ${c.whyNoToi}",
            sector = c.sector,
            tce = "",
            estimatedMag = c.apparentMag,
            targetType = "TIC candidate (catalog status unverified)",
            knownClassification = "Statut catalog nevalidat",
            advisoryPriority = c.scientificPriority,
            scientificReason = "${c.potentialPlanetaryType} | Adâncime: ${c.transitDepthPpm.toInt()} ppm, P: ${c.estimatedPeriodDays?.let { "${String.format(Locale.US, "%.2f", it)} d" } ?: "n/a"}, SNR: ${String.format(Locale.US, "%.1f", c.snrSigma)}σ"
        )
    }

    fun toExoplanetCandidate(c: PureTicCandidate): ExoplanetCandidate {
        val estimatedRadius = 109.2 * sqrt(c.transitDepthPpm / 1_000_000.0) * c.stellarRadiusSun
        return ExoplanetCandidate(
            toiId = "TIC ${c.ticId}",
            ticId = "TIC ${c.ticId}",
            disposition = TfopwgDisposition.CANDIDATE,
            catalogSource = ExoplanetCatalogSource.PURE_TIC_CATALOG,
            orbitalPeriodDays = c.estimatedPeriodDays,
            transitDurationHours = c.transitDurationHours,
            transitDepthPpm = c.transitDepthPpm,
            planetRadiusEarth = estimatedRadius,
            planetMassEarth = null,
            insolationEarthFlux = null,
            equilibriumTempK = null,
            stellarTessMagnitude = c.apparentMag,
            stellarDistancePc = c.distancePc,
            stellarEffTempK = c.stellarTeffK,
            stellarRadiusSun = c.stellarRadiusSun,
            createdDate = null,
            lastUpdated = null,
            worthInvestigatingScore = 0f,
            earthSimilarityIndex = null,
            isInHabitableZone = false,
            isUnvettedCandidate = true,
            anomalyType = "Statut catalog nevalidat",
            discoveryMethod = null,
            discoveryYear = null,
            scoreReasoning = listOf(
                "[TIC • STATUT CATALOG NEVERIFICAT] ${c.discoveryOrigin}",
                "Statut comparație: ${c.whyNoToi}",
                c.scientificNotes,
                c.scientificPriority
            )
        )
    }

}
