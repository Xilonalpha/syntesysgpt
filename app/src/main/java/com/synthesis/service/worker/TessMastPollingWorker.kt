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
import com.synthesis.data.remote.api.ExoplanetArchiveApi
import com.synthesis.data.remote.api.MastExoApi
import com.synthesis.data.remote.api.MastPortalApi
import com.synthesis.data.repository.xilon.PureTicCatalogRepository
import com.synthesis.data.repository.xilon.XilonRepository
import com.synthesis.domain.model.xilon.PureTicCandidate
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

/**
 * Enterprise WorkManager background worker that periodically polls the STScI MAST API
 * and NASA TESS archives for newly processed candidate signals and updates the local Room
 * database (pure_tic_candidates, cached_exoplanets, xilon_discoveries) to keep the planetary list live.
 */
@HiltWorker
class TessMastPollingWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val pureTicCatalogRepo: PureTicCatalogRepository,
    private val xilonRepository: XilonRepository,
    private val mastExoApi: MastExoApi,
    private val mastPortalApi: MastPortalApi,
    private val exoplanetArchiveApi: ExoplanetArchiveApi
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LAST_MAST_POLL_TIMESTAMP).remove(KEY_LAST_SCOUTED_SECTOR).remove(KEY_TOTAL_MAST_CANDIDATES).remove(KEY_LATEST_POLLED_TIC).putString(KEY_LAST_MAST_POLL_STATUS, "PAUSED: provenance-safe survey not implemented").apply()
        Result.success()
    }

    companion object {
        const val TAG = "TessMastPollingWorker"
        const val PREFS_NAME = "tess_mast_polling_prefs"
        const val KEY_LAST_MAST_POLL_TIMESTAMP = "key_last_mast_poll_ts"
        const val KEY_LAST_MAST_POLL_STATUS = "key_last_mast_poll_status"
        const val KEY_LAST_SCOUTED_SECTOR = "key_last_scouted_sector"
        const val KEY_TOTAL_MAST_CANDIDATES = "key_total_mast_candidates"
        const val KEY_LATEST_POLLED_TIC = "key_latest_polled_tic"

        private const val CHANNEL_ID = "channel_tess_mast_polling"
        private const val NOTIFICATION_ID = 4040
    }
}
