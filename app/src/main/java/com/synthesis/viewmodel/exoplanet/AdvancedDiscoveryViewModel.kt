package com.synthesis.viewmodel.exoplanet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synthesis.data.billing.SubscriptionManager
import com.synthesis.data.repository.exoplanet.AdvancedDiscoveryRepository
import com.synthesis.domain.model.exoplanet.*
import com.synthesis.engine.cosmology.BigBangCosmologyEngine
import com.synthesis.engine.cosmology.BlackHoleRelativityEngine
import com.synthesis.engine.exoplanet.*
import com.synthesis.service.*
import com.synthesis.service.worker.MastBatchWorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

enum class AdvancedTab(val title: String, val isYear2026: Boolean = false) {
    // 2026/27 CUTTING-EDGE & COSMOLOGY MODULES:
    EXTRATERRESTRIAL_LIFE("🧬 Viață Extraterestră & Telescoape Live", true),
    MAST_BATCH("⚙️ Motor Procesare Batch MAST", true),
    MAST_SYNC("🛰️ Sincronizare Live MAST / NASA", true),
    FITS_SUMMARY("📋 FITS Header & Observation Summary", true),
    TELESCOPE_VISION("👁️ Telescope Vision & FITS Photometry", true),
    SMART_SCOPE("📡 Smart Scope Sync (Seestar/DWARF)", true),
    BIG_BANG_COSMOLOGY("🌌 Big Bang & Universul Timpuriu", true),
    BLACK_HOLES("🕳️ Găuri Negre & Relativitate Kerr", true),
    PLATO_ASTEROSEISMOLOGY("✨ PLATO Asteroseismology & Age", true),
    JWST_SPECTROSCOPY("🔬 JWST Transmission Spectra", true),
    ROMAN_MICROLENSING("🔭 Roman Microlensing (Rogue Planets)", true),
    AR_SKY_CAMERA("🌌 AR Sky Camera (Live Cer)", true),
    EXOMOON_OCEANS("🌊 Exomoon Inversion & Tidal Oceans", true),
    SPACE_WEATHER("🛡️ Space Weather & Magnetosphere", true),
    // CLASSIC LABORATORY TOOLS:
    BIOSIGNATURES("Astrobiology & Technosignatures", false),
    FALSE_POSITIVE_RESCUE("Rescue False Positives (Gaia De-Blender)", false),
    HARMONIC_STITCHER("Multi-Sector Stitcher", false),
    CLIMATE_SIMULATOR("1D Climate Simulator", false),
    SKY_RADAR("Live Sky Radar & Countdowns", false),
    TELESCOPE_CONTROL("Telescope Physical GoTo & Motors", false),
    LIVE_PHOTOMETRY("Live Photometry & Official Report", false)
}

sealed interface ScientificCalculationState {
    object Idle : ScientificCalculationState
    data class Running(val module: String) : ScientificCalculationState
    data class Success(val result: ScientificCalculationResult) : ScientificCalculationState
    data class Error(val message: String) : ScientificCalculationState
}

sealed interface ScientificCalculationResult {
    data class Timing(val value: LinearEphemeris) : ScientificCalculationResult
    data class Aliases(val value: List<OrbitalAlias>) : ScientificCalculationResult
    data class Contrast(val value: SpectralContrast) : ScientificCalculationResult
    data class Reaction(val value: ReactionEnergy) : ScientificCalculationResult
    data class Asymmetry(val value: TransitAsymmetryEstimate) : ScientificCalculationResult
}

@HiltViewModel
class AdvancedDiscoveryViewModel @Inject constructor(
    private val repository: AdvancedDiscoveryRepository,
    val subscriptionManager: SubscriptionManager,
    private val gpsTransitNotifier: GpsTransitNotifier,
    private val telescopeExportService: TelescopeExportService,
    val telescopeMotorControlService: TelescopeMotorControlService,
    val smartTelescopeSyncService: SmartTelescopeSyncService,
    val romanMicrolensingEngine: RomanMicrolensingEngine,
    val arSkyTrackerService: ArSkyTrackerService,
    val bigBangCosmologyEngine: BigBangCosmologyEngine,
    val blackHoleRelativityEngine: BlackHoleRelativityEngine,
    private val aavsoEtdReportGenerator: AavsoEtdReportGenerator,
    private val photometricFileParser: PhotometricFileParser,
    val celestronNexStarDriverService: CelestronNexStarDriverService,
    private val synthesizedResearchRepository: com.synthesis.data.repository.SynthesizedResearchRepository,
    val telescopeCameraVisionService: TelescopeCameraVisionService,
    val periodicTransitAnalysisEngine: PeriodicTransitAnalysisEngine,
    val fitsPdfReportGenerator: FitsPdfReportGenerator,
    private val agencyReportProtocolService: AgencyReportProtocolService,
    val stellarTemplateLibraryService: StellarTemplateLibraryService,
    val fitsReaderService: FitsReaderService,
    val mastDataSynchronizationService: MastDataSynchronizationService,
    val mastBatchProcessingEngine: MastBatchProcessingEngine,
    val mastBatchWorkManager: MastBatchWorkManager,
    val extraterrestrialLifeService: ExtraterrestrialLifeService,
    val spectralAnomalyService: PlanetarySpectralAnomalyService
) : ViewModel() {

    // EXTRATERRESTRIAL LIFE & REAL TELESCOPES FLEET STATEFLOWS
    private val _extraterrestrialTargets = MutableStateFlow<List<ExtraterrestrialTarget>>(emptyList())
    val extraterrestrialTargets: StateFlow<List<ExtraterrestrialTarget>> = _extraterrestrialTargets.asStateFlow()

    private val _activeTelescopeFeeds = MutableStateFlow<List<ActiveTelescopeTelemetry>>(emptyList())
    val activeTelescopeFeeds: StateFlow<List<ActiveTelescopeTelemetry>> = _activeTelescopeFeeds.asStateFlow()

    private val _liveAstrobiologySyncResult = MutableStateFlow(LiveAstrobiologySyncResult())
    val liveAstrobiologySyncResult: StateFlow<LiveAstrobiologySyncResult> = _liveAstrobiologySyncResult.asStateFlow()

    private val _isSyncingAstrobiology = MutableStateFlow(false)
    val isSyncingAstrobiology: StateFlow<Boolean> = _isSyncingAstrobiology.asStateFlow()

    val mastSyncState: StateFlow<MastSyncState> = mastDataSynchronizationService.syncState
    val curatedMastTargets: List<MastObservationTarget> = mastDataSynchronizationService.getCuratedTargets()
    private val _lastMastResult = MutableStateFlow<MastSyncResult?>(null)
    val lastMastResult: StateFlow<MastSyncResult?> = _lastMastResult.asStateFlow()

    // BATCH PROCESSING ENGINE STATEFLOWS
    val batchJobs: StateFlow<List<MastBatchJob>> = mastBatchProcessingEngine.jobs
    val batchSummary: StateFlow<MastBatchQueueSummary> = mastBatchProcessingEngine.summary
    val isBatchRunning: StateFlow<Boolean> = mastBatchProcessingEngine.isRunning
    val batchCampaigns: List<BatchPresetCampaign> = mastBatchProcessingEngine.getAvailableCampaigns()

    val nexStarTelemetry: StateFlow<NexStarTelemetry> = celestronNexStarDriverService.telemetry
    val telescopeVisionState: StateFlow<TelescopeVisionState> = telescopeCameraVisionService.visionState
    val telescopeViewfinderBitmap: StateFlow<android.graphics.Bitmap?> = telescopeCameraVisionService.viewfinderBitmap

    private val _exportedPdfFile = MutableStateFlow<java.io.File?>(null)
    val exportedPdfFile: StateFlow<java.io.File?> = _exportedPdfFile.asStateFlow()

    private val _periodicTransitResult = MutableStateFlow<PeriodicTransitDetectionResult?>(null)
    val periodicTransitResult: StateFlow<PeriodicTransitDetectionResult?> = _periodicTransitResult.asStateFlow()

    private val _selectedTab = MutableStateFlow(AdvancedTab.EXTRATERRESTRIAL_LIFE)
    val selectedTab: StateFlow<AdvancedTab> = _selectedTab.asStateFlow()

    private val _biosignatures = MutableStateFlow<List<BiosignatureAssessment>>(emptyList())
    val biosignatures: StateFlow<List<BiosignatureAssessment>> = _biosignatures.asStateFlow()

    private val _technosignatures = MutableStateFlow<List<TechnosignatureCandidate>>(emptyList())
    val technosignatures: StateFlow<List<TechnosignatureCandidate>> = _technosignatures.asStateFlow()

    private val _dilutionRescues = MutableStateFlow<List<DilutionRescueCandidate>>(emptyList())
    val dilutionRescues: StateFlow<List<DilutionRescueCandidate>> = _dilutionRescues.asStateFlow()

    private val _harmonicStitches = MutableStateFlow<List<HarmonicStitchCandidate>>(emptyList())
    val harmonicStitches: StateFlow<List<HarmonicStitchCandidate>> = _harmonicStitches.asStateFlow()

    private val _liveTransits = MutableStateFlow<List<LiveTransitTarget>>(emptyList())
    val liveTransits: StateFlow<List<LiveTransitTarget>> = _liveTransits.asStateFlow()

    // 2026 MODULES STATEFLOWS
    val smartScopeTelemetry: StateFlow<SmartTelescopeTelemetry> = smartTelescopeSyncService.telemetry
    val isSmartScopeScanning: StateFlow<Boolean> = smartTelescopeSyncService.isScanning
    val discoveredSmartScopes: StateFlow<List<DiscoveredSmartScope>> = smartTelescopeSyncService.discoveredScopes
    val smartScopeScanStatus: StateFlow<String> = smartTelescopeSyncService.scanStatus

    private val _platoTargets = MutableStateFlow<List<PlatoAsteroseismicResult>>(emptyList())
    val platoTargets: StateFlow<List<PlatoAsteroseismicResult>> = _platoTargets.asStateFlow()

    private val _jwstSpectra = MutableStateFlow<List<JwstTransmissionSpectrumResult>>(emptyList())
    val jwstSpectra: StateFlow<List<JwstTransmissionSpectrumResult>> = _jwstSpectra.asStateFlow()

    // PLANETARY SPECTRAL ANOMALY & PUBLIC REGISTRY COMPARISON
    val curatedRetrievedSpectra: List<RetrievedPlanetarySpectrum> = spectralAnomalyService.getCuratedRetrievedSpectra()
    private val _selectedRetrievedSpectrum = MutableStateFlow<RetrievedPlanetarySpectrum?>(curatedRetrievedSpectra.firstOrNull())
    val selectedRetrievedSpectrum: StateFlow<RetrievedPlanetarySpectrum?> = _selectedRetrievedSpectrum.asStateFlow()

    private val _publicRegistryBenchmarks = MutableStateFlow<List<RegisteredSpectralBenchmark>>(emptyList())
    val publicRegistryBenchmarks: StateFlow<List<RegisteredSpectralBenchmark>> = _publicRegistryBenchmarks.asStateFlow()

    private val _selectedBenchmarkId = MutableStateFlow<String?>(null)
    val selectedBenchmarkId: StateFlow<String?> = _selectedBenchmarkId.asStateFlow()

    private val _spectralComparisonResult = MutableStateFlow<PublicRegistryComparisonResult?>(null)
    val spectralComparisonResult: StateFlow<PublicRegistryComparisonResult?> = _spectralComparisonResult.asStateFlow()

    private val _isComparingSpectra = MutableStateFlow(false)
    val isComparingSpectra: StateFlow<Boolean> = _isComparingSpectra.asStateFlow()

    private val _nasaArchiveDisposition = MutableStateFlow<PublicArchiveDispositionInfo?>(null)
    val nasaArchiveDisposition: StateFlow<PublicArchiveDispositionInfo?> = _nasaArchiveDisposition.asStateFlow()

    private val _isQueryingNasaArchive = MutableStateFlow(false)
    val isQueryingNasaArchive: StateFlow<Boolean> = _isQueryingNasaArchive.asStateFlow()

    private val _exomoons = MutableStateFlow<List<ExomoonTidalOceanResult>>(emptyList())
    val exomoons: StateFlow<List<ExomoonTidalOceanResult>> = _exomoons.asStateFlow()

    private val _spaceWeatherAssessments = MutableStateFlow<List<MagnetosphereShieldingResult>>(emptyList())
    val spaceWeatherAssessments: StateFlow<List<MagnetosphereShieldingResult>> = _spaceWeatherAssessments.asStateFlow()

    // Interactive climate state
    private val _climateResult = MutableStateFlow<ClimateSimulationResult?>(null)
    val climateResult: StateFlow<ClimateSimulationResult?> = _climateResult.asStateFlow()

    var climateInsolation = MutableStateFlow(1.0)
    var climateCo2Bar = MutableStateFlow(0.0004) // 400 ppm
    var climatePressureBar = MutableStateFlow(1.0)

    val isLocked: StateFlow<Boolean> = subscriptionManager.isLocked
    val remainingTrialSeconds: StateFlow<Long> = subscriptionManager.remainingTrialSeconds
    val isPro: StateFlow<Boolean> = subscriptionManager.isPro

    val mountTelemetry: StateFlow<MountTelemetry> = telescopeMotorControlService.telemetry

    private val _exportModalContent = MutableStateFlow<Pair<String, String>?>(null)
    val exportModalContent: StateFlow<Pair<String, String>?> = _exportModalContent.asStateFlow()

    private val _alertSuccessMessage = MutableStateFlow<String?>(null)
    val alertSuccessMessage: StateFlow<String?> = _alertSuccessMessage.asStateFlow()

    // LIVE PHOTOMETRY & DISCOVERY REPORTING SESSION
    private val _liveSession = MutableStateFlow(LiveObservationSession())
    val liveSession: StateFlow<LiveObservationSession> = _liveSession.asStateFlow()

    // PRE-DEFINED STELLAR LIGHT CURVE TEMPLATES LIBRARY (NASA / ESA BASELINES)
    val templates: StateFlow<List<StellarLightCurveTemplate>> =
        MutableStateFlow(stellarTemplateLibraryService.getAllTemplates()).asStateFlow()

    private val _selectedTemplate = MutableStateFlow<StellarLightCurveTemplate?>(
        stellarTemplateLibraryService.getTemplateById("planetary_transit_mandel_agol")
    )
    val selectedTemplate: StateFlow<StellarLightCurveTemplate?> = _selectedTemplate.asStateFlow()

    private val _templateMatchScores = MutableStateFlow<List<TemplateMatchScore>>(emptyList())
    val templateMatchScores: StateFlow<List<TemplateMatchScore>> = _templateMatchScores.asStateFlow()

    private val _isTemplateOverlayActive = MutableStateFlow(true)
    val isTemplateOverlayActive: StateFlow<Boolean> = _isTemplateOverlayActive.asStateFlow()

    private var observationSimulationJob: Job? = null

    init {
        loadData()
        updateClimate("Earth Analog Baseline", 1.0, 0.0004, 1.0)
        initDefaultPhotometrySession()
    }

    fun selectTab(tab: AdvancedTab) {
        _selectedTab.value = tab
    }

    private fun loadData() {
        _extraterrestrialTargets.value = extraterrestrialLifeService.getCuratedExtraterrestrialTargets()
        _activeTelescopeFeeds.value = extraterrestrialLifeService.getActiveTelescopeTelemetryFeeds()
        _biosignatures.value = repository.getCuratedBiosignatures()
        _technosignatures.value = repository.getCuratedTechnosignatures()
        _dilutionRescues.value = repository.getCuratedDilutionRescues()
        _harmonicStitches.value = repository.getCuratedHarmonicStitches()
        _liveTransits.value = repository.getCuratedLiveTransits()
        // 2026 data
        _platoTargets.value = repository.getCuratedPlatoAsteroseismicTargets()
        _jwstSpectra.value = repository.getCuratedJwstSpectra()
        _exomoons.value = repository.getCuratedExomoons()
        _spaceWeatherAssessments.value = repository.getCuratedSpaceWeatherAssessments()
        // Spectral anomaly public registry initialization
        _publicRegistryBenchmarks.value = emptyList()
        runSpectralAnomalyComparison(_selectedRetrievedSpectrum.value, null)
    }

    /**
     * Initializes a real default observation: WASP-12 b empirical observations.
     * Uses deterministic empirical data rather than random generation.
     */
    private fun initDefaultPhotometrySession() {
        // No observation is loaded without an explicit import.
    }

    fun loadEmpiricalWasp12bDataset() {
        _alertSuccessMessage.value = "Set demonstrativ fără proveniență verificabilă; importați observații originale."
    }

    fun importRealPhotometricFile(
        rawText: String,
        candidateName: String? = null,
        hostStar: String? = null
    ) {
        try {
            val result = photometricFileParser.parsePhotometryData(rawText)
            _periodicTransitResult.value = result.periodicTransitResult
            _liveSession.value = LiveObservationSession().copy(
                candidateName = candidateName ?: "Necunoscut",
                hostStar = hostStar ?: "Necunoscut",
                dataPoints = result.points,
                measuredDepthPpm = result.measuredDepthPpm,
                measuredRadiusEarth = result.measuredRadiusEarth,
                calculatedMidTransitBjd = result.calculatedMidBjd,
                expectedDurationMinutes = result.durationMinutes,
                signalToNoiseRatio = result.snr,
                chiSquaredPerDof = result.chiSquaredPerDof,
                isRealImportedData = true,
                dataSourceTag = "Import local FITS/CSV; proveniență neverificată",
                currentPhase = if (result.periodicTransitResult?.isDetected == true) "Semnal periodic BLS preliminar" else "Semnal preliminar nevalidat",
                isObserving = false
            )
            _alertSuccessMessage.value = result.logSummary
        } catch (e: Exception) {
            _alertSuccessMessage.value = "Eroare la parsarea fișierului fotometric: ${e.message}"
        }
    }

    fun updateObserverSettings(
        observerName: String,
        aavsoCode: String,
        location: String,
        telescope: String,
        camera: String,
        filter: String,
        compStar: String,
        compMag: Double,
        checkStar: String,
        checkMag: Double
    ) {
        _liveSession.value = _liveSession.value.copy(
            observerName = observerName,
            aavsoObserverCode = aavsoCode,
            observatoryLocation = location,
            telescopeSpecs = telescope,
            cameraModel = camera,
            filterBand = filter,
            comparisonStarName = compStar,
            comparisonStarMag = compMag,
            checkStarName = checkStar,
            checkStarMag = checkMag
        )
        _alertSuccessMessage.value = "Setările reale ale observatorului au fost actualizate pentru raportul ETD/AAVSO."
    }

    fun refreshActiveModuleData() {
        when (_selectedTab.value) {
            AdvancedTab.SMART_SCOPE -> smartTelescopeSyncService.startNetworkDiscovery()
            AdvancedTab.SKY_RADAR -> {
                _liveTransits.value = repository.getCuratedLiveTransits()
            }
            AdvancedTab.FITS_SUMMARY, AdvancedTab.TELESCOPE_VISION -> {
                loadEmpiricalWasp12bDataset()
            }
            AdvancedTab.LIVE_PHOTOMETRY -> {
                resetPhotometrySession()
            }
            AdvancedTab.EXTRATERRESTRIAL_LIFE, AdvancedTab.BIOSIGNATURES -> {
                _extraterrestrialTargets.value = extraterrestrialLifeService.getCuratedExtraterrestrialTargets()
                _activeTelescopeFeeds.value = extraterrestrialLifeService.getActiveTelescopeTelemetryFeeds()
                _biosignatures.value = repository.getCuratedBiosignatures()
                _technosignatures.value = repository.getCuratedTechnosignatures()
            }
            AdvancedTab.FALSE_POSITIVE_RESCUE -> {
                _dilutionRescues.value = repository.getCuratedDilutionRescues()
            }
            AdvancedTab.HARMONIC_STITCHER -> {
                _harmonicStitches.value = repository.getCuratedHarmonicStitches()
            }
            AdvancedTab.PLATO_ASTEROSEISMOLOGY -> {
                _platoTargets.value = repository.getCuratedPlatoAsteroseismicTargets()
            }
            AdvancedTab.JWST_SPECTROSCOPY -> {
                _jwstSpectra.value = repository.getCuratedJwstSpectra()
            }
            AdvancedTab.EXOMOON_OCEANS -> {
                _exomoons.value = repository.getCuratedExomoons()
            }
            AdvancedTab.SPACE_WEATHER -> {
                _spaceWeatherAssessments.value = repository.getCuratedSpaceWeatherAssessments()
            }
            else -> {
                loadData()
            }
        }
    }

    fun resetPhotometrySession() {
        observationSimulationJob?.cancel()
        loadEmpiricalWasp12bDataset()
    }

    fun generateOfficialEtdReport() {
        val report = aavsoEtdReportGenerator.generateEtdReport(_liveSession.value)
        _exportModalContent.value = Pair("ETD Official Exoplanet Submission Protocol", report)
    }

    fun generateOfficialAavsoReport() {
        val report = aavsoEtdReportGenerator.generateAavsoReport(_liveSession.value)
        _exportModalContent.value = Pair("AAVSO Extended Format (WebObs Upload)", report)
    }

    // --- PLANETARY SPECTRAL ANOMALY & PUBLIC REGISTRY COMPARISON METHODS ---

    fun selectRetrievedSpectrum(spectrum: RetrievedPlanetarySpectrum) {
        _selectedRetrievedSpectrum.value = spectrum
        _nasaArchiveDisposition.value = null
        runSpectralAnomalyComparison(spectrum, _selectedBenchmarkId.value)
    }

    fun selectBenchmarkId(benchmarkId: String?) {
        _selectedBenchmarkId.value = benchmarkId
        runSpectralAnomalyComparison(_selectedRetrievedSpectrum.value, benchmarkId)
    }

    fun runSpectralAnomalyComparison(
        spectrum: RetrievedPlanetarySpectrum? = null,
        benchmarkId: String? = null
    ) {
        viewModelScope.launch {
            _isComparingSpectra.value = true
            val target = spectrum ?: _selectedRetrievedSpectrum.value
            val bench = benchmarkId ?: _selectedBenchmarkId.value
            _spectralComparisonResult.value = null
            _isComparingSpectra.value = false
        }
    }

    fun queryNasaArchiveForCurrentTarget() {
        viewModelScope.launch {
            _isQueryingNasaArchive.value = true
            val targetName = _selectedRetrievedSpectrum.value?.targetName ?: run { _isQueryingNasaArchive.value = false; return@launch }
            _nasaArchiveDisposition.value = null
            _isQueryingNasaArchive.value = false
        }
    }

    /**
     * Generates an official NASA TFOPWG SG1 Seeing-Limited Photometry Log
     * adhering to NExScI and TESS Follow-up Program standards.
     */
    fun generateOfficialNasaTfopReport() {
        val report = agencyReportProtocolService.generateNasaTfopReport(_liveSession.value)
        _exportModalContent.value = Pair("NASA TFOPWG SG1 (NExScI Format)", report)
    }

    /**
     * Generates a standard IVOA VOTable XML document for ingestion into ESA Gaia archives and CDS.
     */
    fun generateOfficialVoTableXml() {
        val xml = agencyReportProtocolService.generateVoTableXml(_liveSession.value)
        _exportModalContent.value = Pair("ESA Gaia / IVOA VOTable XML", xml)
    }

    /**
     * Exports the current FITS summary metadata and light curve charts as a formatted
     * publication-ready PDF report (standard A4 format) for external sharing (IAU/AAVSO/ETD).
     */
    fun exportFitsSummaryPdf(context: android.content.Context, customHeader: FitsHeader? = null): java.io.File? {
        return try {
            val vs = telescopeCameraVisionService.visionState.value
            val effectiveHeader = customHeader
                ?: vs.lastLoadedFitsHeader
                ?: buildDefaultFitsHeaderFallback(_liveSession.value.candidateName, _liveSession.value.hostStar)

            val points = if (vs.photometrySamples.isNotEmpty()) {
                vs.photometrySamples.map { s ->
                    PhotometricDataPoint(
                        minuteOffset = s.timeMinutes,
                        bjdTime = s.bjd,
                        relativeFlux = s.normalizedFlux,
                        fluxError = s.fluxError
                    )
                }
            } else {
                _liveSession.value.dataPoints
            }

            val periodicRes = _periodicTransitResult.value ?: vs.periodicDetectionResult
            val snrThreshold = vs.snrFilterThreshold

            val file = fitsPdfReportGenerator.generateFitsReportPdf(
                context = context,
                header = effectiveHeader,
                session = _liveSession.value,
                periodicResult = periodicRes,
                snrThreshold = snrThreshold,
                dataPoints = points
            )
            _exportedPdfFile.value = file
            _alertSuccessMessage.value = "Raportul PDF '${file.name}' (${(file.length() / 1024)} KB) a fost generat cu succes! Gata pentru partajare externă."
            file
        } catch (e: Exception) {
            _alertSuccessMessage.value = "Eroare la exportul PDF: ${e.message}"
            null
        }
    }

    /**
     * Shares the formatted FITS PDF report via Android share sheet.
     */
    fun shareFitsSummaryPdf(context: android.content.Context, customHeader: FitsHeader? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = _exportedPdfFile.value ?: exportFitsSummaryPdf(context, customHeader)
            if (file != null && file.exists()) {
                val shareIntent = fitsPdfReportGenerator.createShareIntent(context, file)
                val chooser = android.content.Intent.createChooser(shareIntent, "Partajează Raport FITS PDF")
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        }
    }

    /**
     * Opens the formatted FITS PDF report in an external viewer.
     */
    fun openFitsSummaryPdf(context: android.content.Context, customHeader: FitsHeader? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = _exportedPdfFile.value ?: exportFitsSummaryPdf(context, customHeader)
            if (file != null && file.exists()) {
                try {
                    val viewIntent = fitsPdfReportGenerator.createViewIntent(context, file)
                    viewIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(viewIntent)
                } catch (e: Exception) {
                    shareFitsSummaryPdf(context, customHeader)
                }
            }
        }
    }

    fun clearExportedPdfFile() {
        _exportedPdfFile.value = null
    }

    private fun buildDefaultFitsHeaderFallback(targetName: String, hostStar: String): FitsHeader {
        return FitsHeader(
            cards = emptyList(),
            cardMap = emptyMap(),
            simple = true,
            bitPix = 16,
            nAxis = 2,
            dimensions = intArrayOf(2048, 2048),
            bZero = 32768.0,
            bScale = 1.0,
            dateObs = "2026-03-22T21:44:18.25",
            expTimeSeconds = 120.0,
            targetObject = targetName,
            telescope = "Planewave CDK24 0.61m f/6.5",
            instrument = "Andor iKon-L 936 BEX2-DD CCD",
            observer = "Synthesis Astro Laboratory",
            filter = "Sloan r'",
            airmass = 1.14,
            gain = 1.45,
            readNoise = 6.8,
            wcs = FitsWcsInfo(
                crval1 = 68.5524,
                crval2 = 29.8971,
                crpix1 = 1024.0,
                crpix2 = 1024.0,
                cdelt1 = -0.0002,
                cdelt2 = 0.0002,
                ctype1 = "RA---TAN",
                ctype2 = "DEC--TAN"
            )
        )
    }

    fun updateClimate(planetName: String, insolation: Double, co2Bar: Double, pressureBar: Double) {
        climateInsolation.value = insolation
        climateCo2Bar.value = co2Bar
        climatePressureBar.value = pressureBar
        _climateResult.value = if (insolation.isFinite() && insolation > 0 && co2Bar.isFinite() && co2Bar >= 0 && pressureBar.isFinite() && pressureBar > 0 && co2Bar <= pressureBar) repository.simulateClimate(planetName, insolation, co2Bar, pressureBar) else null
    }

    fun rescueCustomCandidate(
        name: String,
        ticId: String,
        status: String,
        depthPpm: Double,
        targetMag: Double,
        neighborMag: Double,
        sepArcsec: Double,
        radSun: Double
    ) {
        _alertSuccessMessage.value = "Corecție indisponibilă: sunt necesare fluxurile măsurate în aceeași bandă și incertitudinile lor."
    }

    fun calculateMeasuredDilution(depth: Double, depthError: Double, target: Double,
                                  targetError: Double, contaminant: Double, contaminantError: Double) {
        viewModelScope.launch {
            try {
                val result = repository.runMeasuredDilution(depth, depthError, target, targetError, contaminant, contaminantError)
                _alertSuccessMessage.value = "Adâncime condițională: ${result.depthPpm} ± ${result.depthErrorPpm} ppm; sursa eclipsei neconfirmată."
            } catch (e: IllegalArgumentException) {
                _alertSuccessMessage.value = "Date fotometrice invalide."
            }
        }
    }

    fun triggerGpsTransitNotification(target: LiveTransitTarget?) {
        val (sent, dynamicMessage) = gpsTransitNotifier.sendGpsTransitAlert(target)
        _alertSuccessMessage.value = if (sent) {
            "Notificare transmisă cu succes:\n\n„$dynamicMessage”"
        } else {
            dynamicMessage
        }
    }

    fun clearAlertMessage() {
        _alertSuccessMessage.value = null
    }

    fun exportTargetFormat(target: LiveTransitTarget, format: String) {
        val content = when (format.uppercase()) {
            "NINA", "N.I.N.A." -> Pair("N.I.N.A. Target CSV", telescopeExportService.exportToNinaCsv(target))
            "STELLARIUM" -> Pair("Stellarium Observation JSON", telescopeExportService.exportToStellariumJson(target))
            else -> Pair("AstroImageJ Aperture Spec", telescopeExportService.exportToAstroImageJ(target))
        }
        _exportModalContent.value = content
    }

    fun dismissExportModal() {
        _exportModalContent.value = null
    }

    // Physical Telescope Mount Actions
    fun connectMountSim() = telescopeMotorControlService.connectSimulation()
    fun connectMountUsb(): UsbConnectResult = telescopeMotorControlService.connectUsbOtg()
    fun connectMountWifi(ip: String, port: Int) = telescopeMotorControlService.connectWifi(ip, port)
    fun disconnectMount() = telescopeMotorControlService.disconnect()
    fun slewMountToTarget(target: LiveTransitTarget) =
        telescopeMotorControlService.slewToTarget(target.candidateName, target.rightAscension, target.declination)
    fun jogMount(direction: SlewDirection, rate: SlewRate) =
        telescopeMotorControlService.pulseManualJog(direction, rate)
    fun emergencyStopMount() = telescopeMotorControlService.emergencyStop()
    fun parkMount() = telescopeMotorControlService.parkMount()
    fun toggleMountTracking() = telescopeMotorControlService.toggleTracking()

    // Smart Scope 2026 Actions
    fun connectSmartScope(model: SmartScopeModel, ip: String, port: Int = model.defaultPort): Boolean =
        smartTelescopeSyncService.connectSmartTelescope(model, ip, port)

    fun slewSmartScopeToTarget(target: LiveTransitTarget) =
        smartTelescopeSyncService.slewToTransitTarget(target)

    fun startSmartScopeSequence(expSec: Double, gain: Int, filter: String) =
        smartTelescopeSyncService.startPhotometricSequence(expSec, gain, filter)

    fun stopSmartScopeSequence() =
        smartTelescopeSyncService.stopObservation()

    fun disconnectSmartScope() =
        smartTelescopeSyncService.disconnect()

    fun startSmartScopeDiscovery() =
        smartTelescopeSyncService.startNetworkDiscovery()

    fun cancelSmartScopeDiscovery() =
        smartTelescopeSyncService.cancelNetworkDiscovery()

    fun addVirtualSmartScope(model: SmartScopeModel) =
        smartTelescopeSyncService.addVirtualTestScope(model)

    // Dedicated Celestron NexStar 6SE Driver Actions
    fun connectNexStarUsb(): Boolean = celestronNexStarDriverService.connectUsbOtg()
    fun connectNexStarWifi(ip: String = "192.168.4.1", port: Int = 4030) = celestronNexStarDriverService.connectWifi(ip, port)
    fun connectNexStarSim() = celestronNexStarDriverService.connectSimulation()
    fun disconnectNexStar() = celestronNexStarDriverService.disconnect()
    fun slewNexStarPrecise(raHours: Double, decDegrees: Double, targetName: String) =
        celestronNexStarDriverService.slewToCoordinatesPrecise(raHours, decDegrees, targetName)
    fun syncNexStarStar(raHours: Double, decDegrees: Double, starName: String) =
        celestronNexStarDriverService.syncCoordinates(raHours, decDegrees, starName)
    fun setNexStarTracking(mode: NexStarTrackingMode) = celestronNexStarDriverService.setTrackingMode(mode)
    fun setNexStarRate(rate: NexStarTrackRate) = celestronNexStarDriverService.setTrackRate(rate)
    fun emergencyStopNexStar() = celestronNexStarDriverService.emergencyStop()
    fun pulseGuideNexStar(direction: NexStarGuideDirection, rate: Int = 1) = celestronNexStarDriverService.pulseGuide(direction, rate)

    // Telescope Camera Vision & FITS Processing Actions
    fun startTelescopeVision(source: TelescopeCameraSource) = telescopeCameraVisionService.startVisionPipeline(source)
    fun stopTelescopeVision() = telescopeCameraVisionService.stopVisionPipeline()
    fun resetTelescopeVisionPhotometry() = telescopeCameraVisionService.resetPhotometry()
    fun selectTelescopeVisionStar(x: Float, y: Float, isTarget: Boolean) =
        telescopeCameraVisionService.selectStarAtCoordinates(x, y, isTarget)

    /**
     * Ingests a FITS file (from local storage, camera, or space agency archive).
     * Automatically differentiates between 2D/3D CCD image frames and Binary Table (BINTABLE)
     * light curves (NASA TESS, Kepler, CHEOPS).
     */
    fun ingestTelescopeFitsBytes(bytes: ByteArray) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val doc = fitsReaderService.read(bytes)
                val extractedLightCurve = doc.extractLightCurve()

                if (extractedLightCurve != null && extractedLightCurve.size >= 5) {
                    // Table Light Curve Mode (NASA Kepler / TESS)
                    val targetObj = doc.header.targetObject ?: doc.header.getString("OBJECT", "Sursă FITS Kepler/TESS")
                    val telescopeName = doc.header.telescope ?: doc.header.getString("TELESCOP", "Observator Spațial")

                    _liveSession.value = LiveObservationSession().copy(
                        candidateName = targetObj,
                        hostStar = "$targetObj ($telescopeName)",
                        dataPoints = extractedLightCurve,
                        isRealImportedData = true,
                        dataSourceTag = "FITS local, proveniență neverificată ($telescopeName)",
                        currentPhase = "Fotometrie FITS importată, proveniență neverificată",
                        isObserving = false
                    )

                    // Run periodic BLS transit scan
                    val blsRes = periodicTransitAnalysisEngine.analyzeDataPoints(extractedLightCurve)
                    _periodicTransitResult.value = blsRes

                    // Run matching against Stellar Template Library
                    runStellarTemplateFit(extractedLightCurve, blsRes.bestPeriodDays)

                    _alertSuccessMessage.value = "Fișier FITS BINTABLE ($targetObj): s-au extras cu succes ${extractedLightCurve.size} puncte fotometrice din fișier."
                } else {
                    if (doc.binaryTable != null) {
                        _alertSuccessMessage.value = "Tabel FITS fără fotometrie temporală validă."
                        return@launch
                    }
                    // 2D CCD/CMOS Image Frame Mode
                    telescopeCameraVisionService.ingestFitsFrame(bytes)
                    val currentPts = _liveSession.value.dataPoints
                    if (currentPts.isNotEmpty()) {
                        runStellarTemplateFit(currentPts, _periodicTransitResult.value?.bestPeriodDays)
                    }
                }
            } catch (e: Exception) {
                _alertSuccessMessage.value = "Import FITS respins: ${e.message}"
            }
        }
    }

    fun selectStellarTemplate(templateId: String) {
        _selectedTemplate.value = stellarTemplateLibraryService.getTemplateById(templateId)
    }

    fun toggleTemplateOverlay() {
        _isTemplateOverlayActive.value = !_isTemplateOverlayActive.value
    }

    /**
     * Cross-correlates the current FITS light curve against all templates in the library.
     */
    fun runStellarTemplateFit(
        customPoints: List<PhotometricDataPoint>? = null,
        periodDays: Double? = null
    ): List<TemplateMatchScore> {
        val points = customPoints ?: _liveSession.value.dataPoints
        val effectivePeriod = periodDays ?: _periodicTransitResult.value?.bestPeriodDays
        val scores = stellarTemplateLibraryService.matchObservedLightCurve(points, effectivePeriod)
        _templateMatchScores.value = scores
        val best = scores.firstOrNull()
        if (best != null) {
            _alertSuccessMessage.value = "Potrivire șablon optimă: ${best.template.name} (${String.format(java.util.Locale.US, "%.1f", best.confidencePercent)}% încredere Bayesiană, χ²_red = ${String.format(java.util.Locale.US, "%.2f", best.reducedChiSquared)})."
        }
        return scores
    }

    /**
     * Executes real-time live data synchronization against STScI MAST.
     * Ingests calibrated PDCSAP fluxes, runs BLS transit search and matches astrophysical templates.
     */
    fun syncMastData(query: String, mission: String = "TESS", targetPeriodDays: Double? = null) {
        viewModelScope.launch {
            val res = mastDataSynchronizationService.syncLiveObservationData(query, mission, targetPeriodDays)
            res.onSuccess { syncResult ->
                _lastMastResult.value = syncResult

                // Update live session with synchronized data
                _liveSession.value = LiveObservationSession().copy(
                    candidateName = syncResult.target.targetName,
                    hostStar = "${syncResult.target.targetName} (${syncResult.target.spectralType})",
                    dataPoints = syncResult.dataPoints,
                    isRealImportedData = true,
                    dataSourceTag = "Arhivă Live STScI MAST (${syncResult.target.mission} Sector ${syncResult.target.sectorOrQuarter})",
                    currentPhase = "Date MAST Sincronizate Live (${syncResult.dataPoints.size} puncte PDCSAP)",
                    isObserving = false
                )

                // Update BLS periodic result
                _periodicTransitResult.value = syncResult.periodicDetection

                // Update template scores
                _templateMatchScores.value = syncResult.templateScores

                // Update success message
                _alertSuccessMessage.value = "Sincronizare MAST reușită! ${syncResult.dataPoints.size} măsurători descărcate și analizate în ${syncResult.executionTimeMs}ms."
            }.onFailure { err ->
                _alertSuccessMessage.value = "Eroare la sincronizarea MAST: ${err.message}"
            }
        }
    }

    // ==========================================
    // BATCH PROCESSING ENGINE ACTIONS
    // ==========================================

    fun startBatchProcessing(context: android.content.Context? = null) {
        mastBatchProcessingEngine.startProcessing()
        context?.let { mastBatchWorkManager.triggerOneTimeBatchJob(it) }
        _alertSuccessMessage.value = "Motorul batch MAST a pornit procesarea în fundal!"
    }

    fun pauseBatchProcessing() {
        mastBatchProcessingEngine.pauseProcessing()
        _alertSuccessMessage.value = "Motorul batch MAST a fost pus pe pauză."
    }

    fun enqueueBatchCampaign(campaign: BatchPresetCampaign) {
        mastBatchProcessingEngine.enqueueCampaign(campaign)
        _alertSuccessMessage.value = "Campania '${campaign.title}' (${campaign.targets.size} ținte) a fost adăugată în coada batch!"
    }

    fun enqueueBulkByText(rawText: String, mission: String = "TESS") {
        val count = mastBatchProcessingEngine.enqueueByTicString(rawText, mission)
        _alertSuccessMessage.value = "$count sisteme stelare au fost adăugate în coada batch!"
    }

    fun cancelBatchJob(jobId: String) {
        mastBatchProcessingEngine.cancelJob(jobId)
    }

    fun clearBatchQueue() {
        mastBatchProcessingEngine.clearQueue()
        _alertSuccessMessage.value = "Coada batch MAST a fost curățată."
    }

    fun retryFailedBatchJobs() {
        mastBatchProcessingEngine.retryFailed()
        _alertSuccessMessage.value = "Reîncercare joburi eșuate în curs..."
    }

    fun inspectBatchJobInLightCurve(job: MastBatchJob) {
        val res = job.result ?: return
        _liveSession.value = LiveObservationSession().copy(
            candidateName = job.targetName,
            hostStar = "${job.targetName} (${job.mission} ${job.sectorOrQuarter})",
            dataPoints = res.dataPoints,
            isRealImportedData = true,
            dataSourceTag = "Batch MAST STScI (${job.mission} TIC ${job.ticId})",
            currentPhase = "Date Fotometrice Batch Ingerate (${res.dataPoints.size} puncte)",
            isObserving = false
        )
        _periodicTransitResult.value = res.periodicDetection
        _templateMatchScores.value = res.templateScores
        _selectedTab.value = AdvancedTab.FITS_SUMMARY
        _alertSuccessMessage.value = "Sistemul stelar '${job.targetName}' a fost transferat pe curba Recharts pentru inspecție!"
    }

    fun updateTelescopeApertureRadii(aperture: Float, skyIn: Float, skyOut: Float) =
        telescopeCameraVisionService.setApertureRadii(aperture, skyIn, skyOut)

    fun runPeriodicBlsScanOnVision(
        minPeriodDays: Double? = null,
        maxPeriodDays: Double? = null,
        hostStarRadius: Double = 1.0,
        hostStarMass: Double = 1.0
    ): PeriodicTransitDetectionResult {
        val res = telescopeCameraVisionService.runPeriodicBlsScan(
            minPeriodDays = minPeriodDays,
            maxPeriodDays = maxPeriodDays,
            hostStarRadiusSolar = hostStarRadius,
            hostStarMassSolar = hostStarMass
        )
        _periodicTransitResult.value = res
        _alertSuccessMessage.value = res.statusSummary
        return res
    }

    fun runBlsAnalysisOnCurrentData(
        minPeriodDays: Double? = null,
        maxPeriodDays: Double? = null,
        hostStarRadius: Double = 1.0,
        hostStarMass: Double = 1.0
    ): PeriodicTransitDetectionResult {
        val points = _liveSession.value.dataPoints
        val result = if (points.isNotEmpty()) {
            periodicTransitAnalysisEngine.analyzeDataPoints(
                points = points,
                minPeriodDays = minPeriodDays,
                maxPeriodDays = maxPeriodDays,
                hostStarRadiusSolar = hostStarRadius,
                hostStarMassSolar = hostStarMass
            )
        } else {
            telescopeCameraVisionService.runPeriodicBlsScan(
                minPeriodDays = minPeriodDays,
                maxPeriodDays = maxPeriodDays,
                hostStarRadiusSolar = hostStarRadius,
                hostStarMassSolar = hostStarMass
            )
        }
        _periodicTransitResult.value = result
        _alertSuccessMessage.value = result.statusSummary
        return result
    }

    fun ingestFitsSeries(files: List<ByteArray>, cadenceMinutes: Double = 5.0, hostStarRadius: Double = 1.0) {
        viewModelScope.launch(Dispatchers.Default) {
            val res = telescopeCameraVisionService.ingestFitsSeries(files, cadenceMinutes, hostStarRadius)
            if (res != null) {
                _periodicTransitResult.value = res
                _alertSuccessMessage.value = "Seria de cadre FITS a fost procesată. ${res.statusSummary}"
            }
        }
    }

    /**
     * Dynamically adjusts the Signal-to-Noise Ratio (SNR) threshold and re-filters the FITS
     * raw pixel buffer in real-time asynchronously on a background dispatcher, ensuring no UI stutter or ANR.
     */
    fun updateSnrFilterThreshold(threshold: Float) {
        viewModelScope.launch(Dispatchers.Default) {
            telescopeCameraVisionService.updateSnrFilterThreshold(threshold)

            // Preserve imported flux and uncertainty; threshold is a vision setting only.
        }
    }

    fun transferVisionDataToOfficialSession() {
        val vs = telescopeCameraVisionService.visionState.value
        if (vs.photometrySamples.isNotEmpty()) {
            val convertedPoints = vs.photometrySamples.map { sample ->
                PhotometricDataPoint(
                    minuteOffset = sample.timeMinutes,
                    bjdTime = sample.bjd,
                    relativeFlux = sample.normalizedFlux,
                    fluxError = sample.fluxError
                )
            }
            _liveSession.value = _liveSession.value.copy(
                dataPoints = convertedPoints,
                measuredDepthPpm = vs.measuredTransitDepthPpm,
                measuredRadiusEarth = vs.measuredPlanetRadiusEarth,
                signalToNoiseRatio = vs.transitSnr,
                chiSquaredPerDof = vs.chiSquared,
                isRealImportedData = false,
                dataSourceTag = "Date cameră/FITS; proveniență neverificată",
                currentPhase = if (vs.isTransitDetected) "Semnal preliminar nevalidat" else "Monitorizare Senzor Optic",
                isObserving = false
            )
            _alertSuccessMessage.value = "Datele fotometrice (${convertedPoints.size} puncte) au fost transferate în sesiunea locală; proveniența rămâne neverificată."
        } else {
            _alertSuccessMessage.value = "Nu există încă date procesate de senzor pentru transfer."
        }
    }

    fun triggerLiveNasaAstrobiologySync() {
        viewModelScope.launch {
            _isSyncingAstrobiology.value = true
            val result = extraterrestrialLifeService.queryLiveHabitableZoneArchive()
            _liveAstrobiologySyncResult.value = result
            _isSyncingAstrobiology.value = false
            _alertSuccessMessage.value = result.statusMessage
        }
    }

    fun saveCurrentModuleToResearchArchive(tab: AdvancedTab) {
        _alertSuccessMessage.value = "Export indisponibil pentru ${tab.title}: lipsesc dovezile observaționale reproductibile."
    }

    private val _scientificCalculation = MutableStateFlow<ScientificCalculationState>(ScientificCalculationState.Idle)
    val scientificCalculation = _scientificCalculation.asStateFlow()
    private var scientificJob: Job? = null
    private var scientificGeneration = 0L

    // Called from the main thread. Only the most recent request may publish a result.
    fun cancelScientificCalculation() {
        scientificGeneration++
        scientificJob?.cancel()
        scientificJob = null
        _scientificCalculation.value = ScientificCalculationState.Idle
    }

    private fun runScientificCalculation(module: String, calculate: suspend () -> ScientificCalculationResult) {
        cancelScientificCalculation()
        val generation = scientificGeneration
        _scientificCalculation.value = ScientificCalculationState.Running(module)
        scientificJob = viewModelScope.launch {
            try {
                val result = calculate()
                if (generation == scientificGeneration) {
                    _scientificCalculation.value = ScientificCalculationState.Success(result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                if (generation == scientificGeneration) {
                    _scientificCalculation.value = ScientificCalculationState.Error("Parametri invalizi sau date insuficiente.")
                }
            } catch (e: Exception) {
                if (generation == scientificGeneration) {
                    _scientificCalculation.value = ScientificCalculationState.Error("Calculul nu a putut fi finalizat.")
                }
            }
        }
    }

    fun calculateTimings(observations: List<TransitTiming>) {
        val input = observations.toList()
        runScientificCalculation("TTV") {
            ScientificCalculationResult.Timing(repository.fitMeasuredTimings(input))
        }
    }

    fun calculateAliases(firstEpoch: Double, secondEpoch: Double, firstError: Double,
        secondError: Double, correlation: Double, minPeriod: Double, maxPeriod: Double) =
        runScientificCalculation("Armonici orbitale") {
            ScientificCalculationResult.Aliases(repository.enumerateMeasuredAliases(firstEpoch,
                secondEpoch, firstError, secondError, correlation, minPeriod, maxPeriod))
        }

    fun calculateSpectralContrast(bins: List<MeasuredSpectralBin>,
        feature: ClosedFloatingPointRange<Double>, continuum: ClosedFloatingPointRange<Double>) {
        val input = bins.toList()
        runScientificCalculation("Contrast spectral") {
            ScientificCalculationResult.Contrast(repository.evaluateMeasuredContrast(input, feature, continuum))
        }
    }

    fun calculateReaction(temperatureK: Double, standardGibbs: Double, species: List<ReactionSpecies>) {
        val input = species.toList()
        runScientificCalculation("Energie Gibbs") {
            ScientificCalculationResult.Reaction(repository.evaluateReaction(temperatureK, standardGibbs, input))
        }
    }

    fun calculateAsymmetry(ingress: Double, egress: Double, ingressError: Double,
        egressError: Double, correlation: Double) =
        runScientificCalculation("Asimetrie") {
            ScientificCalculationResult.Asymmetry(repository.evaluateMeasuredAsymmetry(ingress,
                egress, ingressError, egressError, correlation))
        }
}
