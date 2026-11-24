package com.synthesis.viewmodel.exoplanet

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synthesis.data.repository.exoplanet.UnvettedHuntRepository
import com.synthesis.domain.model.exoplanet.*
import com.synthesis.service.worker.TessSurveyWorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DeepHuntTab(val title: String) {
    MONO_TRANSITS("Mono-Transits (27d Blindspot)"),
    TESS_SURVEY_SCOUT("TESS Survey Scout (WorkManager)"),
    TTV_INVERSION("TTV Gravitational Inversion"),
    ATMOSPHERIC_TSM("JWST Atmospheric TSM"),
    NEGLECTED_JEWELS("NASA Backlog Purgatory"),
    INDEPENDENT_SEARCH("Independent FITS search")
}

@HiltViewModel
class DeepHuntViewModel @Inject constructor(
    private val repository: UnvettedHuntRepository,
    private val xilonRepository: com.synthesis.data.repository.xilon.XilonRepository,
    private val mastSynchronization: com.synthesis.service.MastDataSynchronizationService,
    private val synthesizedResearchRepository: com.synthesis.data.repository.SynthesizedResearchRepository,
    private val tessSurveyWorkManager: TessSurveyWorkManager
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(DeepHuntTab.MONO_TRANSITS)
    val selectedTab: StateFlow<DeepHuntTab> = _selectedTab.asStateFlow()

    private val _monoTransits = MutableStateFlow<List<MonoTransitCandidate>>(emptyList())
    val monoTransits: StateFlow<List<MonoTransitCandidate>> = _monoTransits.asStateFlow()

    private val _ttvSystems = MutableStateFlow<List<TtvCompanionAnalysis>>(emptyList())
    val ttvSystems: StateFlow<List<TtvCompanionAnalysis>> = _ttvSystems.asStateFlow()

    private val _atmosphericScores = MutableStateFlow<List<AtmosphericCharacterizationScore>>(emptyList())
    val atmosphericScores: StateFlow<List<AtmosphericCharacterizationScore>> = _atmosphericScores.asStateFlow()

    private val _neglectedJewels = MutableStateFlow<List<NeglectedJewelTarget>>(emptyList())
    val neglectedJewels: StateFlow<List<NeglectedJewelTarget>> = _neglectedJewels.asStateFlow()

    // WorkManager TESS Survey Scout State
    private val _scoutStatus = MutableStateFlow<TessSurveyScoutStatus?>(null)
    val scoutStatus: StateFlow<TessSurveyScoutStatus?> = _scoutStatus.asStateFlow()

    private val _surveyCriteria = MutableStateFlow<TessSurveyCriteria>(TessSurveyCriteria())
    val surveyCriteria: StateFlow<TessSurveyCriteria> = _surveyCriteria.asStateFlow()

    // Interactive custom single-transit inversion solver state
    private val _customCandidate = MutableStateFlow<MonoTransitCandidate?>(null)
    val customCandidate: StateFlow<MonoTransitCandidate?> = _customCandidate.asStateFlow()

    init {
        loadData()
    }

    private val _independentRunning = MutableStateFlow(false)
    val independentRunning = _independentRunning.asStateFlow()
    private val _independentStatus = MutableStateFlow("")
    val independentStatus = _independentStatus.asStateFlow()

    fun searchIndependentTargets(input: String) {
        if (_independentRunning.value) return
        val targets = input.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }.distinct()
        if (targets.size !in 1..5 || targets.any { !it.matches(Regex("[0-9]+")) }) {
            _independentStatus.value = "Enter 1–5 numeric TIC identifiers"
            return
        }
        _independentRunning.value = true
        _independentStatus.value = ""
        viewModelScope.launch {
            try {
                for (tic in targets) {
                    val line = try {
                        val result = xilonRepository.runIndependentSearch(tic, mastSynchronization).getOrThrow()
                        val b = result.periodicDetection
                        "TIC $tic: " + (if (b.isDetected) "Preliminary P=${b.bestPeriodDays} d; SNR=${b.transitSnr}" else "No accepted signal") +
                            "; experiment=${result.experimentId}; " + (if (b.isDetected) xilonRepository.compareCatalogPeriods(tic, b.bestPeriodDays) else "novelty not assessed")
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "TIC $tic: incomplete — ${e.message}"
                    }
                    _independentStatus.value += line + "\n"
                }
            } finally {
                _independentRunning.value = false
            }
        }
    }

    fun selectTab(tab: DeepHuntTab) {
        _selectedTab.value = tab
    }

    fun loadData() {
        _monoTransits.value = repository.getCuratedMonoTransits()
        _ttvSystems.value = repository.getCuratedTtvSystems()
        _atmosphericScores.value = repository.getTopAtmosphericTsmScores()
        _neglectedJewels.value = repository.getNeglectedJewels()
    }

    fun loadScoutStatus(context: Context) {
        _scoutStatus.value = tessSurveyWorkManager.getScoutStatus(context)
        _surveyCriteria.value = tessSurveyWorkManager.loadCriteria(context)
    }

    fun triggerImmediateSurvey(context: Context) {
        tessSurveyWorkManager.triggerImmediateSurvey(context, _surveyCriteria.value)
        loadScoutStatus(context)
    }

    fun updateSurveyCriteria(context: Context, criteria: TessSurveyCriteria) {
        _surveyCriteria.value = criteria
        tessSurveyWorkManager.saveCriteria(context, criteria)
        loadScoutStatus(context)
    }

    fun togglePeriodicScout(context: Context, enabled: Boolean) {
        if (enabled) {
            tessSurveyWorkManager.schedulePeriodicSurvey(context, repeatHours = 6, criteria = _surveyCriteria.value)
        } else {
            tessSurveyWorkManager.cancelPeriodicSurvey(context)
        }
        loadScoutStatus(context)
    }

    fun solveCustomSingleTransit(
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
        impactB: Double = 0.0
    ) {
        viewModelScope.launch {
            val result = try { repository.analyzeCustomSingleTransit(
                ticId = ticId,
                candidateName = candidateName,
                sector = sector,
                epochBtjd = epochBtjd,
                durationHours = durationHours,
                depthPpm = depthPpm,
                teffK = teffK,
                radSun = radSun,
                massSun = massSun,
                distPc = distPc,
                vMag = vMag,
                impactB = impactB
            ) } catch (e: IllegalArgumentException) { return@launch }
            _customCandidate.value = result
            // Prepend to list
            _monoTransits.value = listOf(result) + _monoTransits.value.filter { it.ticId != ticId }
        }
    }

    fun saveCandidateToResearchArchive(candidate: MonoTransitCandidate) {
        // Export disabled until observation provenance and measured statistics are available.
    }
}
