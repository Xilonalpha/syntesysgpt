package com.synthesis

import com.synthesis.engine.terra.TerraFrontierEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TerraFrontierEngineTest {

    private lateinit var engine: TerraFrontierEngine

    @Before
    fun setUp() {
        engine = TerraFrontierEngine()
    }

    @Test
    fun testHadalPressureAtChallengerDeep() {
        val pressureAtm = engine.computeHadalPressure(10994)
        // Challenger deep is around 1080 - 1100 atmospheres
        assertTrue("Pressure should be extreme (> 1000 atm)", pressureAtm > 1000.0)
        assertTrue("Pressure should be physically realistic (< 1200 atm)", pressureAtm < 1200.0)
    }

    @Test
    fun testAbsolutePressureBoundary() {
        assertEquals(1.0, engine.computeHadalPressure(0), 1e-12)
        val expected = 1.0 + 1025.0 * 1.0021 * 9.80665 * 1000.0 / 101325.0
        assertEquals(expected, engine.computeHadalPressure(1000), 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeDepthRejected() {
        engine.computeHadalPressure(-1)
    }

    @Test
    fun testMantleHydrationBudgetExceedsSurfaceOceans() {
        val defaultMantleWater = engine.computeMantleHydrationBudget(1.5)
        assertEquals("Ringwoodit (γ-olivină) & Wadsleyit (β-fază)", defaultMantleWater.mineralPhase)
        assertTrue("Mantle water mass should exceed 1e21 kg", defaultMantleWater.estimatedWaterMassKg > 1e21)
        assertTrue("Mantle water should be at least 1.4x all surface oceans combined", defaultMantleWater.ratioToSurfaceOceans >= 1.4)
    }

    @Test
    fun testGeomagneticDecayAndPolarDrift() {
        val state2026 = engine.simulateGeomagneticDecayAndDrift(2026)
        assertEquals(22.4, state2026.currentIntensityMicroTesla, 0.1)
        assertTrue("Polar drift should be around 48 km/year", state2026.northMagneticDriftKmPerYear in 40.0..60.0)
        assertFalse(state2026.isSplittingIntoDoubleCore)
        assertTrue(state2026.regionName.contains("Simulare"))
        assertTrue(state2026.lowEarthOrbitRadiationRisk.startsWith("Nevalidat"))
    }

    @Test
    fun testHadalTrenchesCatalogCompleteness() {
        val trenches = engine.getHadalTrenchCatalog()
        assertTrue("Should contain at least 5 major hadal trenches", trenches.size >= 5)
        trenches.forEach { trench ->
            assertTrue("Depth must be hadal (> 6000m)", trench.maxDepthMeters > 6000)
            assertTrue("Unmapped percentage must be high (> 70%)", trench.unmappedFloorPercent >= 70.0)
            assertFalse(trench.primaryEndemicOrganisms.isEmpty())
        }
    }

    @Test
    fun testFrontierMysteriesContent() {
        val mysteries = engine.getFrontierMysteries()
        assertEquals(7, mysteries.size)
        mysteries.forEach { mystery ->
            assertTrue(mystery.misconception.isNotBlank())
            assertTrue(mystery.scientificDiscovery.isNotBlank())
            assertTrue(mystery.physicalPrinciple.isNotBlank())
            assertTrue(mystery.explorationReadinessPercent in 0..100)
        }
    }
}
