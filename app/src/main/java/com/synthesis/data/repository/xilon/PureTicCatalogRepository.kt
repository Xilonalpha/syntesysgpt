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
                require(tic.matches(Regex("[0-9]+")))
                val additional = j.get("additional_signal_id")?.asString
                require(if (additional == null) row.id == "independent-tess:$experiment"
                    else additional.matches(Regex("[0-9a-f-]{36}")) && row.id == "independent-tess:$experiment:$additional")
                require(string("catalog_status") == "NO_PERIOD_MATCH_IN_QUERIED_TABLES; NOVELTY_UNVERIFIED")
                require(string("vetting_status") == "PRELIMINARY_SCREEN_PASSED")
                val evidence = string("comparison_evidence")
                val depth = number("depth_ppm")
                require(depth < 1_000_000.0)
                val snr = number("snr")
                val noise = j.get("residual_noise")?.takeIf { it.isJsonObject }?.asJsonObject
                fun optionalFinite(key: String): Double? = runCatching {
                    noise?.get(key)?.asDouble?.takeIf { it.isFinite() }
                }.getOrNull()
                val beta = optionalFinite("blockNoiseRatio")?.takeIf { it >= 0 }
                val rho = optionalFinite("lagOneCorrelation")?.takeIf { it in -1.0..1.0 }
                val adjusted = beta?.let { snr / maxOf(1.0, it) }
                val noiseNote = if (adjusted != null && rho != null)
                    String.format(Locale.US, "; SNR ajustat %.2f; beta %.2f; rho1 %.3f; diagnostic necalibrat", adjusted, beta, rho)
                else "; diagnostic zgomot indisponibil"

                val phaseNote = when (j.get("phase_validation")?.asString) {
                    "CONDITIONAL_TIMING_COMPATIBILITY" -> {
                        val phase = j.get("phase_evidence")?.takeIf { it.isJsonObject }?.asJsonObject
                        val valid = runCatching {
                            phase != null && additional == null &&
                                phase.get("allCompatible").asBoolean &&
                                phase.get("trainingSector").asInt > 0 &&
                                phase.get("heldOutSector").asInt > phase.get("trainingSector").asInt &&
                                phase.getAsJsonArray("comparisons").size() >= 2 &&
                                phase.getAsJsonArray("comparisons").all {
                                    it.asJsonObject.get("compatible").asBoolean &&
                                        it.asJsonObject.get("cycleUnambiguous").asBoolean
                                } &&
                                abs(phase.getAsJsonObject("ephemeris").get("periodDays").asDouble -
                                    number("period_days")) / number("period_days") <= 0.01
                        }.getOrDefault(false)
                        if (valid) "; compatibilitate temporala conditionata raportata; dovezi nereverificate la afisare"
                        else "; dovezi temporale incomplete sau inconsistente"
                    }
                    "COMPATIBILITY_NOT_ESTABLISHED" -> "; compatibilitate temporala nestabilita"
                    "ASSESSMENT_INCOMPLETE" -> "; evaluare multisector incompleta"
                    "NOT_ASSESSED_ADDITIONAL_SIGNAL" -> "; semnal suplimentar fara validare temporala proprie"
                    else -> "; validare temporala indisponibila"
                }

                PureTicCandidate(
                    ticId = tic, name = "TIC $tic", sector = string("sector"),
                    apparentMag = j.get("tmag").asDouble.also { require(it.isFinite()) },
                    estimatedPeriodDays = number("period_days"),
                    transitDurationHours = number("duration_hours"), transitDepthPpm = depth,
                    stellarTeffK = number("stellar_teff"), stellarRadiusSun = number("stellar_radius"),
                    stellarMassSun = number("stellar_mass"), distancePc = number("distance_pc"),
                    discoveryOrigin = if (additional == null) "Analiza fotometrica TESS independenta" else "Cautare BLS suplimentara pe aceleasi observatii",
                    whyNoToi = "Fara potrivire de perioada in tabelele interogate; noutate neconfirmata",
                    potentialPlanetaryType = "Semnal de tranzit nevalidat",
                    scientificNotes = "Experiment: $experiment; comparatie: $evidence; filtrare preliminara trecuta, validare independenta necesara" + noiseNote + phaseNote,
                    scientificPriority = "TRIAJ OBSERVATIONAL • SNR ${String.format(Locale.US, "%.1f", snr)} (necalibrat)",
                    isLiveIngested = true, ingestionTimestamp = row.timestamp, snrSigma = snr
                )
            } catch (_: Exception) { null }
        }.let { candidates -> candidates.map { candidate ->
            val sector = candidate.sector.trim().toIntOrNull()
                ?: Regex("(?i)^sector\\s+(\\d+)$").matchEntire(candidate.sector.trim())?.groupValues?.get(1)?.toIntOrNull()
            val period = candidate.estimatedPeriodDays
            val matches = if (sector == null || sector <= 0 || period == null) emptyList() else candidates.filter { other ->
                val otherSector = other.sector.trim().toIntOrNull()
                    ?: Regex("(?i)^sector\\s+(\\d+)$").matchEntire(other.sector.trim())?.groupValues?.get(1)?.toIntOrNull()
                val otherPeriod = other.estimatedPeriodDays
                other.ticId == candidate.ticId && otherSector != null && otherSector > 0 && otherSector != sector &&
                    otherPeriod != null && abs(otherPeriod - period) / period <= 0.01 &&
                    abs(other.transitDepthPpm - candidate.transitDepthPpm) / candidate.transitDepthPpm <= 0.3 &&
                    abs(other.transitDurationHours - candidate.transitDurationHours) / candidate.transitDurationHours <= 0.3
            }
            if (matches.isEmpty()) candidate else candidate.copy(scientificNotes = candidate.scientificNotes +
                "; concordanta preliminara intre etichete de sector distincte; independenta produselor si faza neverificate")
        } }.sortedWith(compareByDescending<PureTicCandidate> { it.snrSigma }
            .thenByDescending { it.ingestionTimestamp }.thenBy { it.ticId })
            .let { ranked ->
                val retained = mutableListOf<PureTicCandidate>()
                for (candidate in ranked) {
                    val period = candidate.estimatedPeriodDays ?: continue
                    if (retained.none { previous ->
                        val previousPeriod = previous.estimatedPeriodDays
                        previous.ticId == candidate.ticId && previousPeriod != null &&
                            abs(previousPeriod - period) / maxOf(previousPeriod, period) <= 0.01
                    }) retained.add(candidate)
                }
                retained
            } }
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())
    fun getPureTicCatalog(): List<PureTicCandidate> = dynamicCatalogFlow.value
    fun getCandidatesByTic(ticId: String): List<PureTicCandidate> =
        dynamicCatalogFlow.value.filter { it.ticId == ticId }

    // Legacy single-target callers receive the highest-ranked signal.
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
