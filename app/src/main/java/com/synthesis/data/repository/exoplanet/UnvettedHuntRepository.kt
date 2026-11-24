package com.synthesis.data.repository.exoplanet

import com.synthesis.domain.model.exoplanet.*
import com.synthesis.engine.exoplanet.AtmosphericTsmEngine
import com.synthesis.engine.exoplanet.MonoTransitHunterEngine
import com.synthesis.engine.exoplanet.TtvInversionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnvettedHuntRepository @Inject constructor(
    private val monoTransitHunterEngine: MonoTransitHunterEngine,
    private val ttvInversionEngine: TtvInversionEngine,
    private val atmosphericTsmEngine: AtmosphericTsmEngine
) {

    /**
     * Curated catalog of real TESS & Kepler single-transit events that fell into
     * NASA's 27.4-day sector blindspot.
     */
    fun getCuratedMonoTransits(): List<MonoTransitCandidate> = emptyList()
    fun getCuratedTtvSystems(): List<TtvCompanionAnalysis> = emptyList()
    fun getTopAtmosphericTsmScores(): List<AtmosphericCharacterizationScore> = emptyList()
    fun getNeglectedJewels(): List<NeglectedJewelTarget> = emptyList()

    suspend fun analyzeCustomSingleTransit(
        ticId: String,
        candidateName: String,
        sector: String,
        epochBtjd: Double,
        durationHours: Double,
        depthPpm: Double,
        teffK: Double,
        radSun: Double,
        massSun: Double,
        distPc: Double?,
        vMag: Double,
        impactB: Double
    ): MonoTransitCandidate = withContext(Dispatchers.Default) {
        monoTransitHunterEngine.analyzeSingleTransit(
            ticId = ticId,
            candidateName = candidateName,
            sector = sector,
            epochBtjd = epochBtjd,
            transitDurationHours = durationHours,
            transitDepthPpm = depthPpm,
            stellarTeffK = teffK,
            stellarRadiusSun = radSun,
            stellarMassSun = massSun,
            stellarDistancePc = distPc,
            apparentMagV = vMag,
            impactParameter = impactB
        )
    }
}
