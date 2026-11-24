package com.synthesis.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synthesis.data.repository.SynthesisRepository
import com.synthesis.domain.model.*
import com.synthesis.service.worker.AstronomicalSyncManager
import com.synthesis.service.worker.CelestialSyncInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SynthesisRepository,
    private val astronomicalSyncManager: AstronomicalSyncManager
) : ViewModel() {

    val investigations: StateFlow<List<Investigation>> = repository.getInvestigations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val discoveries: StateFlow<List<Discovery>> = repository.getAllDiscoveries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTasks: StateFlow<List<Investigation>> = investigations.map { list ->
        list.filter { it.status == InvestigationStatus.ACTIVE || it.status == InvestigationStatus.ANALYZING || it.status == InvestigationStatus.CORRELATING || it.status == InvestigationStatus.PENDING }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _systemStatus = MutableStateFlow(
        SystemStatus(dataSources = 0, liveConnections = 0, activeAnalyses = 0, anomalies = 0)
    )
    val systemStatus: StateFlow<SystemStatus> = _systemStatus.asStateFlow()

    private val _syncInfo = MutableStateFlow(
        CelestialSyncInfo(0L, "SCHEDULED (1h WorkManager)", 0, 0, 0f)
    )
    val syncInfo: StateFlow<CelestialSyncInfo> = _syncInfo.asStateFlow()

    init {
        viewModelScope.launch {
            for (id in listOf("disc_wasp39b_co2", "disc_saa_geomag", "disc_tic25155310_usp")) {
                repository.deleteDiscoveryById(id)
            }
        }
    }

    fun loadSyncInfo(context: Context) {
        _syncInfo.value = astronomicalSyncManager.getSyncInfo(context)
    }

    fun triggerLiveSync(context: Context) {
        astronomicalSyncManager.triggerImmediateSync(context)
        _syncInfo.value = _syncInfo.value.copy(status = "SYNCING LIVE...")
    }

    fun createInvestigation(title: String, query: String) {
        viewModelScope.launch { repository.createInvestigation(title, query) }
    }

    fun initiateResearchProject(
        title: String,
        query: String,
        domain: String = "Astrophysics",
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            val investigation = repository.createInvestigation(title = title, query = query)
            repository.updateInvestigationStatus(investigation.id, InvestigationStatus.ACTIVE)
            onCreated(investigation.id)
        }
    }

    data class SystemStatus(
        val dataSources: Int,
        val liveConnections: Int,
        val activeAnalyses: Int,
        val anomalies: Int
    )
}
