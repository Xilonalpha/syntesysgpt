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
    private val cachedExoplanetDao: CachedExoplanetDao
) {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Legacy rows have no verifiable provenance. Keep them on disk but quarantine them.
    val dynamicCatalogFlow: StateFlow<List<PureTicCandidate>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    fun getPureTicCatalog(): List<PureTicCandidate> = emptyList()
    fun getCandidateByTic(ticId: String): PureTicCandidate? = null

    fun toTargetStarCandidate(c: PureTicCandidate): TargetStarCandidate {
        return TargetStarCandidate(
            ticId = c.ticId,
            name = c.name,
            description = "[TIC PUR • FĂRĂ TOI] ${c.discoveryOrigin}. ${c.whyNoToi}",
            sector = c.sector,
            tce = "",
            estimatedMag = c.apparentMag,
            targetType = "Pure TIC Candidate (Fără TOI)",
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
                "[TIC PUR • FĂRĂ TOI] ${c.discoveryOrigin}",
                "Motiv lipsă TOI: ${c.whyNoToi}",
                c.scientificNotes,
                c.scientificPriority
            )
        )
    }

}
