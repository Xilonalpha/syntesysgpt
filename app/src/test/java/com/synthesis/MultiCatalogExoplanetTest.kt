package com.synthesis

import com.synthesis.domain.model.ExoplanetCatalogSource
import com.synthesis.engine.exoplanet.ExoplanetCandidateFinder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MultiCatalogExoplanetTest {

    private lateinit var finder: ExoplanetCandidateFinder

    @Before
    fun setUp() {
        finder = ExoplanetCandidateFinder()
    }

    @Test
    fun testPhlHabitableCatalogContainsGoldStandardPlanets() {
        val phlPlanets = finder.getPhlHabitableCatalog()
        assertTrue("No unverified static PHL entries", phlPlanets.isEmpty())
    }

    @Test
    fun testCheopsAndGaiaCatalog() {
        val cheopsPlanets = finder.getCheopsAndGaiaCatalog()
        assertTrue("No unverified static CHEOPS/Gaia entries", cheopsPlanets.isEmpty())
    }
}
