package com.synthesis.service.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.synthesis.MainActivity
import com.synthesis.data.repository.exoplanet.TessArchiveSurveyRepository
import com.synthesis.domain.model.exoplanet.TessSurveyCriteria
import com.synthesis.domain.model.exoplanet.TessSurveyDiscovery
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@HiltWorker
class TessSurveyScannerWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tessArchiveSurveyRepository: TessArchiveSurveyRepository,
    private val xilonRepository: com.synthesis.data.repository.xilon.XilonRepository,
    private val synchronization: com.synthesis.service.MastDataSynchronizationService,
    private val researchRepository: com.synthesis.data.repository.SynthesizedResearchRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            Log.d(TAG, "Starting background periodic TESS survey scan...")

            // Read configured criteria from preferences
            val maxTmag = prefs.getFloat(KEY_CRITERIA_MAX_TMAG, 12.0f).toDouble()
            val minSnr = prefs.getFloat(KEY_CRITERIA_MIN_SNR, 7.1f).toDouble()
            val minDepth = prefs.getFloat(KEY_CRITERIA_MIN_DEPTH_PPM, 400.0f).toDouble()
            val dwarfOnly = prefs.getBoolean(KEY_CRITERIA_DWARF_ONLY, true)
            val unvettedOnly = prefs.getBoolean(KEY_CRITERIA_UNVETTED_ONLY, true)
            val batchSize = prefs.getInt(KEY_CRITERIA_BATCH_SIZE, 6)

            val criteria = TessSurveyCriteria(
                maxTmag = maxTmag,
                minSnr = minSnr,
                minDepthPpm = minDepth,
                dwarfStarsOnly = dwarfOnly,
                unvettedNonToiPreferred = unvettedOnly,
                scanBatchSize = batchSize
            )

            // Independent evidence is archived by the research service, not counted as a confirmed discovery.
            if (unvettedOnly) {
                val page = prefs.getInt("independent_tic_page", 1).coerceAtLeast(1)
                val signals = xilonRepository.runIndependentSurveyPage(
                    maxTmag, page, batchSize.coerceIn(1, 10), synchronization
                ) { observation, comparison, comparisonEvidence ->
                    val d = observation.periodicDetection
                    val experiment = requireNotNull(observation.experimentId)
                    val tic = observation.target.ticId
                    if (d.transitSnr < minSnr || d.transitDepthPpm < minDepth) return@runIndependentSurveyPage
                    val star = xilonRepository.fetchStarProperties(tic).getOrThrow()
                    val radius = star.radiusSun
                    if (dwarfOnly && (radius == null || !radius.isFinite() || radius <= 0.0 || radius > 1.35)) return@runIndependentSurveyPage
                    val payload = com.google.gson.JsonObject().apply {
                        addProperty("experiment_id", experiment)
                        addProperty("tic", tic)
                        addProperty("period_days", d.bestPeriodDays)
                        addProperty("depth_ppm", d.transitDepthPpm)
                        addProperty("snr", d.transitSnr)
                        addProperty("catalog_status", comparison)
                        addProperty("comparison_evidence", comparisonEvidence)
                    }
                    researchRepository.saveResearch(com.synthesis.data.local.entity.SynthesizedResearchEntity(
                        id = "independent-tess:$experiment",
                        title = "Semnal TESS nevalidat: TIC $tic",
                        targetName = "TIC $tic",
                        researchCategory = "EXOPLANET",
                        summary = "Analiza fotometrica independenta; $comparison. Noutate neconfirmata.",
                        synthesizedDataJson = payload.toString(),
                        confidenceScore = 0f,
                        sourceCount = observation.products.size,
                        isVerified = false
                    ))
                }
                prefs.edit()
                    .putInt("independent_tic_page", if (page == Int.MAX_VALUE) 1 else page + 1)
                    .putLong(KEY_LAST_RUN_TIMESTAMP, System.currentTimeMillis())
                    .putString(KEY_LAST_RUN_STATUS, "INDEPENDENT_PAGE_${page}: ${signals.size} archived signals; novelty unverified")
                    .apply()
                return@withContext Result.success()
            }

            val batch = tessArchiveSurveyRepository.executeMeasuredSurveyBatch(criteria)
            val discoveries = batch.candidates
            val nowMs = System.currentTimeMillis()

            if (discoveries.isNotEmpty()) {
                Log.i(TAG, "Scout reanalyzed ${discoveries.size} potential transits! Posting system notification...")
                postTransitAlertNotification(discoveries)
            } else {
                Log.d(TAG, "Scout completed. No candidates above criteria thresholds in this batch.")
            }

            // Save telemetry
            val currentTotal = prefs.getInt(KEY_TOTAL_STARS_EVALUATED, 0) + batch.evaluated
            val currentDiscTotal = prefs.getInt(KEY_DISCOVERIES_COUNT, 0) + discoveries.size

            prefs.edit()
                .putLong(KEY_LAST_RUN_TIMESTAMP, nowMs)
                .putString(KEY_LAST_RUN_STATUS, if (batch.evaluated == 0) "NO_COMPLETED_ANALYSES" else "CATALOG_REANALYSIS")
                .putInt(KEY_TOTAL_STARS_EVALUATED, currentTotal)
                .putInt(KEY_DISCOVERIES_COUNT, currentDiscTotal)
                .apply {
                    if (discoveries.isNotEmpty()) {
                        putString(KEY_LAST_DISCOVERED_TIC, discoveries.first().ticId)
                    }
                }
                .apply()

            Result.success()
        } catch (e: CancellationException) {
            Log.d(TAG, "TESS survey scanner background task cancelled by WorkManager.")
            prefs.edit()
                .putString(KEY_LAST_RUN_STATUS, "STANDBY (Anulat/Oprit)")
                .apply()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "TESS survey scanner background task failed: ${e.message}", e)
            prefs.edit()
                .putLong(KEY_LAST_RUN_TIMESTAMP, System.currentTimeMillis())
                .putString(KEY_LAST_RUN_STATUS, "FAILED: ${e.localizedMessage}")
                .apply()

            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun postTransitAlertNotification(discoveries: List<TessSurveyDiscovery>) {
        ensureNotificationChannelExists()

        val primary = discoveries.first()
        val title = "🔭 Alertă Tranzit TESS: TIC ${primary.ticId}"
        val shortContent = "Semnal potențial de tranzit detectat (${primary.depthPpm.toInt()} ppm, SNR ${String.format(Locale.US, "%.1f", primary.snr)}σ). Rază ~${String.format(Locale.US, "%.1f", primary.derivedRadiusEarth)} R⊕ pe stea Tmag ${String.format(Locale.US, "%.1f", primary.hostStarTmag)}."

        val bigText = buildString {
            append("★ ${primary.starName}\n")
            append("• Adâncime: ${primary.depthPpm.toInt()} ppm (Δm: ${String.format(Locale.US, "%.4f", primary.depthMagnitudeDelta)} mag)\n")
            append("• Perioadă orbitală estimată: ${String.format(Locale.US, "%.3f", primary.orbitalPeriodDays)} zile\n")
            append("• Durată tranzit: ${String.format(Locale.US, "%.1f", primary.transitDurationHours)} ore\n")
            append("• Raport semnal/zgomot (necalibrat): SNR ${String.format(Locale.US, "%.1f", primary.snr)}σ\n")
            append("• Diagnostic: ${primary.verdictLabel}\n")
            if (discoveries.size > 1) {
                append("\n+ încă ${discoveries.size - 1} candidați descoperiți în acest sondaj!")
            }
            append("\nSalvat automat în Baza de Date Room pentru inspecție.")
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("target_tic_id", primary.ticId)
            putExtra("open_screen", "xilon")
        }

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(shortContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(appContext)
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted for background transit alert.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send transit alert notification: ${e.message}")
        }
    }

    private fun ensureNotificationChannelExists() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TESS Exoplanet Discovery Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerte automate pentru tranzite exoplanetare identificate de sondajul de fundal"
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val TAG = "TessSurveyScannerWorker"
        const val PREFS_NAME = "synthesis_tess_survey_scout"
        const val CHANNEL_ID = "channel_tess_transit_alerts"
        const val NOTIFICATION_ID = 8841

        const val KEY_LAST_RUN_TIMESTAMP = "key_last_run_timestamp"
        const val KEY_LAST_RUN_STATUS = "key_last_run_status"
        const val KEY_TOTAL_STARS_EVALUATED = "key_total_stars_evaluated"
        const val KEY_DISCOVERIES_COUNT = "key_discoveries_count"
        const val KEY_LAST_DISCOVERED_TIC = "key_last_discovered_tic"

        const val KEY_CRITERIA_MAX_TMAG = "key_criteria_max_tmag"
        const val KEY_CRITERIA_MIN_SNR = "key_criteria_min_snr"
        const val KEY_CRITERIA_MIN_DEPTH_PPM = "key_criteria_min_depth_ppm"
        const val KEY_CRITERIA_DWARF_ONLY = "key_criteria_dwarf_only"
        const val KEY_CRITERIA_UNVETTED_ONLY = "key_criteria_unvetted_only"
        const val KEY_CRITERIA_BATCH_SIZE = "key_criteria_batch_size"
    }
}
