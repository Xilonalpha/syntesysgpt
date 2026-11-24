package com.synthesis.service

import com.synthesis.data.local.entity.SynthesizedResearchEntity
import com.synthesis.data.repository.SynthesizedResearchRepository
import com.synthesis.domain.model.exoplanet.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise-grade batch processing engine allowing users to queue multiple MAST datasets
 * for background analysis, enabling large-scale automated planet candidate discovery
 * across multiple star systems.
 */
@Singleton
class MastBatchProcessingEngine @Inject constructor(
    private val mastDataSynchronizationService: MastDataSynchronizationService,
    private val synthesizedResearchRepository: SynthesizedResearchRepository
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var processingJob: Job? = null

    private val _jobs = MutableStateFlow<List<MastBatchJob>>(emptyList())
    val jobs: StateFlow<List<MastBatchJob>> = _jobs.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _config = MutableStateFlow(BatchEngineConfig())
    val config: StateFlow<BatchEngineConfig> = _config.asStateFlow()

    private val _discoveryEvents = MutableSharedFlow<MastBatchJob>(extraBufferCapacity = 64)
    val discoveryEvents: SharedFlow<MastBatchJob> = _discoveryEvents.asSharedFlow()

    private fun computeSummary(jobList: List<MastBatchJob>, running: Boolean): MastBatchQueueSummary {
        val total = jobList.size
        val queued = jobList.count { it.status == MastBatchJobStatus.QUEUED }
        val inProg = jobList.count { it.status == MastBatchJobStatus.DOWNLOADING || it.status == MastBatchJobStatus.ANALYZING }
        val completed = jobList.count { it.status == MastBatchJobStatus.COMPLETED }
        val failed = jobList.count { it.status == MastBatchJobStatus.FAILED }
        val discoveries = jobList.count { it.isCandidateDiscovered }
        val totalPts = jobList.sumOf { it.result?.dataPoints?.size ?: 0 }
        val execTimes = jobList.mapNotNull { it.result?.executionTimeMs }
        val avgTime = if (execTimes.isNotEmpty()) execTimes.average().toLong() else 0L

        return MastBatchQueueSummary(
            totalJobs = total,
            queuedCount = queued,
            inProgressCount = inProg,
            completedCount = completed,
            failedCount = failed,
            candidatesDiscoveredCount = discoveries,
            totalPhotometricPointsAnalyzed = totalPts,
            averageExecutionTimeMs = avgTime,
            isRunning = running
        )
    }

    private val _summary = MutableStateFlow(MastBatchQueueSummary())
    val summary: StateFlow<MastBatchQueueSummary> = _summary.asStateFlow()

    private fun refreshSummary() {
        _summary.value = computeSummary(_jobs.value, _isRunning.value)
    }

    private fun updateJobsList(transform: (List<MastBatchJob>) -> List<MastBatchJob>) {
        _jobs.update(transform)
        refreshSummary()
    }

    // Curated campaign presets
    private val campaigns = emptyList<BatchPresetCampaign>()

    fun getAvailableCampaigns(): List<BatchPresetCampaign> = campaigns

    fun updateConfig(newConfig: BatchEngineConfig) {
        _config.value = newConfig
    }

    /**
     * Enqueues an individual target for batch processing.
     */
    fun enqueueTarget(target: MastObservationTarget, priority: Int = 3): String {
        val jobId = "batch_${UUID.randomUUID().toString().take(8)}"
        val job = MastBatchJob(
            id = jobId,
            ticId = target.ticId,
            targetName = target.targetName,
            mission = target.mission,
            sectorOrQuarter = target.sectorOrQuarter,
            priority = priority
        )
        updateJobsList { it + job }
        return jobId
    }

    /**
     * Enqueues a list of targets.
     */
    fun enqueueTargets(targets: List<MastObservationTarget>, priority: Int = 3) {
        val newJobs = targets.map { t ->
            MastBatchJob(
                id = "batch_${UUID.randomUUID().toString().take(8)}",
                ticId = t.ticId,
                targetName = t.targetName,
                mission = t.mission,
                sectorOrQuarter = t.sectorOrQuarter,
                priority = priority
            )
        }
        updateJobsList { it + newJobs }
    }

    /**
     * Enqueues a pre-configured campaign.
     */
    fun enqueueCampaign(campaign: BatchPresetCampaign) {
        enqueueTargets(campaign.targets)
    }

    /**
     * Enqueues targets by parsing a raw text string containing comma, space,
     * or newline-separated TIC IDs (e.g. "384159646, 268301217, 100100827").
     */
    fun enqueueByTicString(rawText: String, mission: String = "TESS"): Int {
        val delimiters = charArrayOf(',', ';', '\n', '\r', '\t', ' ')
        val tokens = rawText.split(*delimiters)
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .map { it.replace("TIC", "").replace("TOI-", "").trim() }
            .filter { it.isNotEmpty() && it.all { ch -> ch.isDigit() || ch == '.' || ch == '-' } }
            .distinct()

        val newJobs = tokens.map { cleanTic ->
            MastBatchJob(
                id = "batch_${UUID.randomUUID().toString().take(8)}",
                ticId = cleanTic,
                targetName = "TIC $cleanTic",
                mission = mission,
                sectorOrQuarter = "Auto"
            )
        }
        if (newJobs.isNotEmpty()) {
            updateJobsList { it + newJobs }
        }
        return newJobs.size
    }

    /**
     * Starts or resumes queue processing in the background.
     */
    fun startProcessing() {
        if (_isRunning.value) return
        _isRunning.value = true
        refreshSummary()

        processingJob?.cancel()
        processingJob = engineScope.launch {
            processQueueLoop()
        }
    }

    /**
     * Pauses the batch engine. Currently active downloads/analyses finish gracefully.
     */
    fun pauseProcessing() {
        _isRunning.value = false
        refreshSummary()
        processingJob?.cancel()
        processingJob = null
    }

    fun cancelJob(jobId: String) {
        updateJobsList { list ->
            list.map { if (it.id == jobId && (it.status == MastBatchJobStatus.QUEUED || it.status == MastBatchJobStatus.DOWNLOADING)) {
                it.copy(status = MastBatchJobStatus.CANCELLED, statusMessage = "Anulat de utilizator")
            } else it }
        }
    }

    fun clearCompleted() {
        updateJobsList { list ->
            list.filter { it.status != MastBatchJobStatus.COMPLETED && it.status != MastBatchJobStatus.CANCELLED }
        }
    }

    fun clearQueue() {
        pauseProcessing()
        updateJobsList { emptyList() }
    }

    fun retryFailed() {
        updateJobsList { list ->
            list.map { if (it.status == MastBatchJobStatus.FAILED || it.status == MastBatchJobStatus.CANCELLED) {
                it.copy(
                    status = MastBatchJobStatus.QUEUED,
                    progressFraction = 0f,
                    statusMessage = "În așteptare pentru reîncercare...",
                    errorMessage = null
                )
            } else it }
        }
        startProcessing()
    }

    /**
     * Main background queue consumption loop with controlled concurrency.
     */
    private suspend fun processQueueLoop() {
        val semaphore = Semaphore(_config.value.concurrency.coerceIn(1, 4))

        while (_isRunning.value && currentCoroutineContext().isActive) {
            val nextJob = _jobs.value
                .filter { it.status == MastBatchJobStatus.QUEUED }
                .minByOrNull { it.priority }

            if (nextJob == null) {
                // Check if all in-progress jobs finished
                val inProgress = _jobs.value.any { it.status == MastBatchJobStatus.DOWNLOADING || it.status == MastBatchJobStatus.ANALYZING }
                if (!inProgress) {
                    _isRunning.value = false
                    break
                }
                delay(400)
                continue
            }

            // Launch single job processor with semaphore
            engineScope.launch {
                semaphore.withPermit {
                    if (_isRunning.value) {
                        processSingleJob(nextJob.id)
                    }
                }
            }

            delay(150)
        }
    }

    private suspend fun processSingleJob(jobId: String) {
        updateJob(jobId) {
            it.copy(
                status = MastBatchJobStatus.DOWNLOADING,
                startedTimestampMs = System.currentTimeMillis(),
                progressFraction = 0.2f,
                statusMessage = "Conectare STScI MAST: descărcare PDCSAP pentru TIC ${it.ticId}..."
            )
        }

        val job = _jobs.value.find { it.id == jobId } ?: return

        try {
            updateJob(jobId) { it.copy(progressFraction = 0.5f, statusMessage = "Procesare serie temporală...") }

            // Execute live sync and multi-engine analysis
            val syncResultResult = mastDataSynchronizationService.syncLiveObservationData(
                query = job.ticId,
                mission = job.mission
            )

            if (syncResultResult.isFailure) {
                val ex = syncResultResult.exceptionOrNull()
                updateJob(jobId) {
                    it.copy(
                        status = MastBatchJobStatus.FAILED,
                        completedTimestampMs = System.currentTimeMillis(),
                        errorMessage = ex?.message ?: "Eroare la descărcarea datelor MAST",
                        statusMessage = "Eșuat: ${ex?.message ?: "Eroare rețea/analiză"}"
                    )
                }
                return
            }

            val syncResult = syncResultResult.getOrThrow()

            updateJob(jobId) {
                it.copy(
                    status = MastBatchJobStatus.ANALYZING,
                    progressFraction = 0.8f,
                    statusMessage = "Vetting astrofizic: BLS, Bayesian & potrivire șabloane..."
                )
            }

            // Vetting & Classification
            val cfg = _config.value
            val bls = syncResult.periodicDetection
            require(!syncResult.experimentId.isNullOrBlank() && syncResult.products.isNotEmpty() &&
                    bls.periodogram.isNotEmpty()) { "Incomplete BLS scan or missing experiment evidence" }
            val bestTemplate = syncResult.templateScores.firstOrNull()

            val snr = bls.transitSnr
            val depthPpm = bls.transitDepthFraction * 1_000_000.0
            val period = bls.bestPeriodDays

            val isTransitModel = bestTemplate?.template?.category == StellarTemplateCategory.PLANETARY_TRANSIT
            val isEclipsingBinary = bestTemplate?.template?.category == StellarTemplateCategory.ECLIPSING_BINARY
            val isFlare = bestTemplate?.template?.category == StellarTemplateCategory.STELLAR_FLARE

            val isDiscovered = bls.isDetected && snr >= cfg.minSnrThreshold &&
                    depthPpm >= cfg.minDepthPpm &&
                    isTransitModel &&
                    (bestTemplate?.confidencePercent ?: 0.0) >= 55.0

            val disposition = when {
                isDiscovered -> "Semnal preliminar nevalidat (U-Shape, SNR ${String.format(Locale.US, "%.1f", snr)})"
                isEclipsingBinary -> "Binară cu Eclipsă (V-Shape / Eclipsă Secundară)"
                isFlare -> "Erupție Stelară (Creștere Rapidă / Scădere Exponențială)"
                snr >= 5.0 -> "Semnal Ambiguitate Scăzută (Necesită Urmărire)"
                else -> "Zgomot Instrumental / Fără Tranzit Semnificativ"
            }

            val completedJob = job.copy(
                status = MastBatchJobStatus.COMPLETED,
                progressFraction = 1.0f,
                statusMessage = "Finalizat cu succes! ($disposition)",
                completedTimestampMs = System.currentTimeMillis(),
                result = syncResult,
                isCandidateDiscovered = isDiscovered,
                candidatePeriodDays = period,
                candidateDepthPpm = depthPpm,
                candidateSnr = snr,
                bestTemplateName = bestTemplate?.template?.name,
                bestTemplateConfidence = bestTemplate?.confidencePercent,
                dispositionLabel = disposition
            )

            updateJob(jobId) { completedJob }

            // Save to Room DB if candidate discovered
            if (isDiscovered && cfg.autoSaveDiscoveriesToDb) {
                saveCandidateDiscoveryToResearchArchive(completedJob)
            }

            if (isDiscovered) {
                _discoveryEvents.emit(completedJob)
            }

        } catch (e: CancellationException) {
            updateJob(jobId) { it.copy(status = MastBatchJobStatus.CANCELLED, statusMessage = "Anulat") }
            throw e
        } catch (e: Exception) {
            updateJob(jobId) {
                it.copy(
                    status = MastBatchJobStatus.FAILED,
                    completedTimestampMs = System.currentTimeMillis(),
                    errorMessage = e.message,
                    statusMessage = "Eroare: ${e.message}"
                )
            }
        }
    }

    private fun updateJob(jobId: String, transform: (MastBatchJob) -> MastBatchJob) {
        updateJobsList { list ->
            list.map { if (it.id == jobId) transform(it) else it }
        }
    }

    private suspend fun saveCandidateDiscoveryToResearchArchive(job: MastBatchJob) {
        val result = job.result ?: return
        val entity = SynthesizedResearchEntity(
            id = "discovery_${job.ticId}_${System.currentTimeMillis()}",
            title = "Candidat Planetar Batch MAST: ${job.targetName}",
            targetName = job.targetName,
            researchCategory = "EXOPLANET",
            summary = "Descoperire automată prin procesare batch MAST STScI. Tranzit la P = ${String.format(Locale.US, "%.4f", job.candidatePeriodDays)} zile, adâncime ${String.format(Locale.US, "%.0f", job.candidateDepthPpm)} ppm, SNR = ${String.format(Locale.US, "%.1f", job.candidateSnr)}. Șablon optim: ${job.bestTemplateName}.",
            synthesizedDataJson = """{"ticId":"${job.ticId}","mission":"${job.mission}","periodDays":${job.candidatePeriodDays},"depthPpm":${job.candidateDepthPpm},"snr":${job.candidateSnr},"template":"${job.bestTemplateName}","pointsCount":${result.dataPoints.size}}""",
            confidenceScore = 0f,
            sourceCount = result.dataPoints.size,
            observerNotes = "Descoperire generată în fundal de MastBatchProcessingEngine.",
            isVerified = false,
            isBookmarked = true
        )
        synthesizedResearchRepository.saveResearch(entity)
    }
}
