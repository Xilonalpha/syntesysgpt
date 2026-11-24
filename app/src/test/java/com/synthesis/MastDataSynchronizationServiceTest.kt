package com.synthesis

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.synthesis.data.remote.api.*
import com.synthesis.data.remote.dto.*
import com.synthesis.engine.exoplanet.BayesianUncertaintyEngine
import com.synthesis.service.MastDataSynchronizationService
import com.synthesis.service.PeriodicTransitAnalysisEngine
import com.synthesis.service.StellarTemplateLibraryService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MastDataSynchronizationServiceTest {

    // Lightweight mock implementations for Unit Testing
    private val mockMastExoApi = object : MastExoApi {
        override suspend fun getTces(ticId: String): JsonObject {
            val root = JsonObject()
            val arr = JsonArray()
            arr.add("s0039-s0039:TCE_1")
            root.add("TCE", arr)
            return root
        }

        override suspend fun getLightCurveTable(ticId: String, tce: String, sector: String): JsonObject {
            val root = JsonObject()
            val data = JsonArray()
            for (i in 0 until 40) {
                val row = JsonObject()
                val phase = -0.15 + (i * 0.0075)
                val flux = if (phase in -0.02..0.02) 0.988 else 1.000
                row.addProperty("PHASE", phase)
                row.addProperty("LC_DETREND", flux)
                data.add(row)
            }
            root.add("data", data)
            return root
        }

        override suspend fun getDvInfo(ticId: String, tce: String, sector: String): JsonObject {
            val root = JsonObject()
            val hdr = JsonObject()
            hdr.addProperty("TDUR", 4.2)
            hdr.addProperty("TDEPTH", 12000.0)
            hdr.addProperty("TPERIOD", 3.52)
            root.add("DV Data Header", hdr)
            return root
        }
    }

    private val mockMastPortalApi = object : MastPortalApi {
        override suspend fun invokeService(requestJson: String): JsonObject = JsonObject()
    }

    private val mockExoplanetArchiveApi = object : ExoplanetArchiveApi {
        override suspend fun queryToiTable(adqlQuery: String, format: String): List<TessObjectOfInterestDto> = emptyList()
        override suspend fun queryKeplerTable(adqlQuery: String, format: String): List<KeplerKoiDto> = emptyList()
        override suspend fun queryK2Table(adqlQuery: String, format: String): List<K2CandidateDto> = emptyList()
        override suspend fun queryConfirmedTable(adqlQuery: String, format: String): List<ConfirmedExoplanetDto> = emptyList()
    }

    private val bayesianEngine = BayesianUncertaintyEngine()
    private val periodicEngine = PeriodicTransitAnalysisEngine(bayesianEngine)
    private val templateService = StellarTemplateLibraryService()

    private val service = MastDataSynchronizationService(
        mastExoApi = mockMastExoApi,
        mastPortalApi = mockMastPortalApi,
        exoplanetArchiveApi = mockExoplanetArchiveApi,
        periodicTransitAnalysisEngine = periodicEngine,
        bayesianUncertaintyEngine = bayesianEngine,
        stellarTemplateLibraryService = templateService,
        context = android.test.mock.MockContext()
    )

    @Test
    fun testNoInventedCuratedTargets() {
        assertTrue(service.getCuratedTargets().isEmpty())
    }

    @Test
    fun testPhaseOnlyDataCannotBecomeTimeSeries() = runBlocking {
        assertTrue(service.syncLiveObservationData("TOI-1895.01", "TESS").isFailure)
    }

    @Test
    fun testUnknownTargetCannotGenerateFallback() = runBlocking {
        assertTrue(service.syncLiveObservationData("TIC 999999999", "TESS").isFailure)
    }
}
