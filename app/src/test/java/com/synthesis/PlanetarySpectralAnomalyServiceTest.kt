package com.synthesis

import com.synthesis.data.remote.api.ExoplanetArchiveApi
import com.synthesis.data.remote.dto.*
import com.synthesis.data.repository.exoplanet.PublicSpectralDatasetRegistry
import com.synthesis.domain.model.exoplanet.*
import com.synthesis.service.PlanetarySpectralAnomalyService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlanetarySpectralAnomalyServiceTest {

    private lateinit var registry: PublicSpectralDatasetRegistry
    private lateinit var mockApi: ExoplanetArchiveApi
    private lateinit var service: PlanetarySpectralAnomalyService

    @Before
    fun setUp() {
        registry = PublicSpectralDatasetRegistry()
        mockApi = object : ExoplanetArchiveApi {
            override suspend fun queryToiTable(adqlQuery: String, format: String): List<TessObjectOfInterestDto> = emptyList()
            override suspend fun queryKeplerTable(adqlQuery: String, format: String): List<KeplerKoiDto> = emptyList()
            override suspend fun queryK2Table(adqlQuery: String, format: String): List<K2CandidateDto> = emptyList()
            override suspend fun queryConfirmedTable(adqlQuery: String, format: String): List<ConfirmedExoplanetDto> {
                return if (adqlQuery.contains("WASP-39")) {
                    listOf(
                        ConfirmedExoplanetDto(
                            plName = "WASP-39 b",
                            hostName = "WASP-39",
                            ticId = null,
                            discoveryMethod = "Transit",
                            discYear = 2011,
                            discFacility = "WASP-South / JWST Transiting Exoplanet ERS",
                            plOrbPer = 4.055,
                            plTranDur = 2.8,
                            plTranDep = 21200.0,
                            plRadE = 14.3,
                            plBmassE = 89.0,
                            plEqt = 1166.0,
                            plInsol = null,
                            syDist = 215.0,
                            syVmag = 12.1,
                            stTeff = 5485.0,
                            stRad = 0.93
                        )
                    )
                } else {
                    emptyList()
                }
            }
        }
        service = PlanetarySpectralAnomalyService(registry, mockApi)
    }

    @Test
    fun testPublicRegistryContainsExpectedSpaceTelescopeBenchmarks() {
        val benchmarks = registry.getAllBenchmarks()
        assertTrue("Registry must contain published benchmarks", benchmarks.isNotEmpty())

        val wasp39b = registry.findBenchmarkById("NASA-JWST-WASP-39b-PRISM")
        assertNotNull("WASP-39 b JWST ERS benchmark must be present", wasp39b)
        assertTrue(wasp39b!!.verifiedMolecules.contains("CO2"))
        assertTrue(wasp39b.verifiedMolecules.contains("SO2"))

        val k218b = registry.findBenchmarkById("NASA-JWST-K2-18b-NIRSPEC")
        assertNotNull("K2-18 b Hycean benchmark must be present", k218b)
        assertTrue(k218b!!.verifiedMolecules.contains("CH4"))
    }

    @Test
    fun testIdentifyMoleculesAtWavelength() {
        val co2Bands = registry.identifyMoleculesAtWavelength(4.30)
        assertTrue("4.30 um should match CO2 or PH3", co2Bands.any { it.moleculeFormula == "CO2" || it.moleculeFormula == "PH3" })

        val so2Bands = registry.identifyMoleculesAtWavelength(4.05)
        assertTrue("4.05 um should match SO2 photochemistry", so2Bands.any { it.moleculeFormula == "SO2" })

        val dmsBands = registry.identifyMoleculesAtWavelength(3.40)
        assertTrue("3.40 um should match DMS biosignature candidate", dmsBands.any { it.moleculeFormula == "DMS" })
        assertTrue(registry.isExoticOrBiosignatureMolecule("DMS"))
    }

    @Test fun testNoInventedRetrievedSpectra() {
        assertTrue(service.getCuratedRetrievedSpectra().isEmpty())
    }

    @Test fun testArchiveQueryFailsClosed() = runBlocking {
        try {
            service.queryNasaArchiveForTarget("WASP-39 b")
            fail("Unverified archive query must not claim coverage")
        } catch (expected: UnsupportedOperationException) { }
    }
}
