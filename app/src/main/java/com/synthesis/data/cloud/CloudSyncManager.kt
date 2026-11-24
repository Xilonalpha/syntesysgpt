package com.synthesis.data.cloud

import com.google.gson.Gson
import com.synthesis.data.local.dao.InvestigationDao
import com.synthesis.data.local.dao.XilonDiscoveryDao
import com.synthesis.domain.model.exoplanet.LiveObservationSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class CloudSyncStatus {
    IDLE,
    SYNCING,
    SYNCED,
    OFFLINE_QUEUED,
    ERROR,
    LOCAL_EXPORT
}

data class SyncTelemetry(
    val status: CloudSyncStatus = CloudSyncStatus.IDLE,
    val lastSyncTimestamp: Long = 0L,
    val pendingRecordsCount: Int = 0,
    val remoteCloudEndpoint: String = "",
    val statusMessage: String = "Sistemul de sincronizare este pregătit."
)

data class CloudBackupBundle(
    val exportTimestamp: Long,
    val appVersion: String = "1.5.0",
    val investigationsJson: String,
    val discoveriesJson: String,
    val photometricSession: LiveObservationSession? = null
)

/**
 * Cloud Synchronization Architecture Manager.
 * Orchestrates multi-device synchronization, offline queues, conflict resolution,
 * and JSON cloud backup/restore for astronomical datasets and photometric sessions.
 */
@Singleton
class CloudSyncManager @Inject constructor(
    private val investigationDao: InvestigationDao,
    private val discoveryDao: XilonDiscoveryDao,
    private val gson: Gson
) {
    private val _telemetry = MutableStateFlow(SyncTelemetry())
    val telemetry: StateFlow<SyncTelemetry> = _telemetry.asStateFlow()

    suspend fun createExportBackup(currentSession: LiveObservationSession?): String = withContext(Dispatchers.IO) {
        val investigations = investigationDao.getAll().first()
        val discoveries = discoveryDao.getAllDiscoveries().first()

        val bundle = CloudBackupBundle(
            exportTimestamp = System.currentTimeMillis(),
            investigationsJson = gson.toJson(investigations),
            discoveriesJson = gson.toJson(discoveries),
            photometricSession = currentSession
        )

        _telemetry.value = _telemetry.value.copy(
            status = CloudSyncStatus.LOCAL_EXPORT,
            statusMessage = "Export JSON local (date neverificate) generat (${investigations.size} investigații, ${discoveries.size} descoperiri)."
        )

        gson.toJson(bundle)
    }

    suspend fun triggerCloudSync(): Boolean = withContext(Dispatchers.IO) {
        _telemetry.value = _telemetry.value.copy(
            status = CloudSyncStatus.ERROR,
            statusMessage = "Sincronizare cloud indisponibilă: nu există un transport remote implementat."
        )
        false
    }
}
