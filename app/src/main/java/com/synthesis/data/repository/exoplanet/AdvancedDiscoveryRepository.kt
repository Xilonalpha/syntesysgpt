package com.synthesis.data.repository.exoplanet

import com.synthesis.domain.model.exoplanet.*
import com.synthesis.engine.exoplanet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdvancedDiscoveryRepository @Inject constructor(
    private val biosignatureEngine: BiosignatureTechnosignatureEngine,
    private val dilutionEngine: DilutionDeBlenderEngine,
    private val harmonicStitcherEngine: MultiSectorHarmonicStitcherEngine,
    private val climateEngine: EnergyBalanceClimateEngine,
    private val skyRadarEngine: TransitSkyRadarEngine,
    private val platoEngine: PlatoAsteroseismologyEngine,
    private val jwstEngine: JwstTransmissionSpectroscopyEngine,
    private val exomoonEngine: ExomoonTidalOceanEngine,
    private val spaceWeatherEngine: SpaceWeatherMagnetosphereEngine,
    private val ttvEngine: TtvInversionEngine
) {

    fun getCuratedPlatoAsteroseismicTargets(): List<PlatoAsteroseismicResult> = emptyList()

    fun getCuratedJwstSpectra(): List<JwstTransmissionSpectrumResult> = emptyList()

    fun getCuratedExomoons(): List<ExomoonTidalOceanResult> = emptyList()

    fun getCuratedSpaceWeatherAssessments(): List<MagnetosphereShieldingResult> = emptyList()

    fun getCuratedBiosignatures(): List<BiosignatureAssessment> = emptyList()

    fun getCuratedTechnosignatures(): List<TechnosignatureCandidate> = emptyList()

    fun getCuratedDilutionRescues(): List<DilutionRescueCandidate> = emptyList()

    fun getCuratedHarmonicStitches(): List<HarmonicStitchCandidate> = emptyList()

    fun simulateClimate(
        planetName: String,
        insolation: Double,
        co2Bar: Double,
        pressureBar: Double
    ): ClimateSimulationResult {
        return climateEngine.simulateClimate(
            planetName = planetName,
            stellarInsolationSolar = insolation,
            co2LevelBar = co2Bar,
            surfacePressureBar = pressureBar
        )
    }

    private fun getCurrentBtjd(): Double {
        val nowMs = System.currentTimeMillis()
        val jd = (nowMs / 86400000.0) + 2440587.5
        return jd - 2457000.0
    }

    fun getCuratedLiveTransits(): List<LiveTransitTarget> = emptyList()

    suspend fun runMeasuredDilution(depth: Double, depthError: Double, target: Double,
                                    targetError: Double, contaminant: Double, contaminantError: Double
    ): ApertureDilutionEstimate = withContext(Dispatchers.Default) {
        dilutionEngine.deblendMeasured(depth, depthError, target, targetError, contaminant, contaminantError)
    }

    suspend fun fitMeasuredTimings(observations: List<TransitTiming>): LinearEphemeris =
        withContext(Dispatchers.Default) { ttvEngine.fitMeasuredTimings(observations) }

    suspend fun enumerateMeasuredAliases(firstEpoch: Double, secondEpoch: Double,
        firstErrorDays: Double, secondErrorDays: Double, correlation: Double,
        minPeriodDays: Double, maxPeriodDays: Double): List<OrbitalAlias> =
        withContext(Dispatchers.Default) {
            harmonicStitcherEngine.enumerateMeasuredAliases(firstEpoch, secondEpoch,
                firstErrorDays, secondErrorDays, correlation, minPeriodDays, maxPeriodDays)
        }

    suspend fun evaluateMeasuredContrast(bins: List<MeasuredSpectralBin>,
        feature: ClosedFloatingPointRange<Double>, continuum: ClosedFloatingPointRange<Double>): SpectralContrast =
        withContext(Dispatchers.Default) { jwstEngine.evaluateMeasuredContrast(bins, feature, continuum) }

    suspend fun evaluateReaction(temperatureK: Double, standardGibbsJPerMol: Double,
        species: List<ReactionSpecies>): ReactionEnergy =
        withContext(Dispatchers.Default) { biosignatureEngine.evaluateReaction(temperatureK, standardGibbsJPerMol, species) }

    suspend fun evaluateMeasuredAsymmetry(ingressHours: Double, egressHours: Double,
        ingressErrorHours: Double, egressErrorHours: Double, correlation: Double): TransitAsymmetryEstimate =
        withContext(Dispatchers.Default) {
            biosignatureEngine.evaluateMeasuredAsymmetry(ingressHours, egressHours,
                ingressErrorHours, egressErrorHours, correlation)
        }
}
