package com.synthesis

import com.synthesis.engine.exoplanet.TransitTimingPhysics
import com.synthesis.engine.exoplanet.TransitTimingPhysics.SectorEpoch
import org.junit.Assert.*
import org.junit.Test

class SectorPhaseComparisonTest {
    private val first = SectorEpoch(1, 2460000.0, 0.001, "a".repeat(64))
    private val second = SectorEpoch(14, 2460300.002, 0.001, "b".repeat(64))

    @Test fun compatibleEpochs() {
        val r = TransitTimingPhysics.compareSectors(first, second, 3.0, 0.0001)
        assertEquals(100L, r.cycle)
        assertEquals(0.002, r.residualDays, 1e-8)
        assertEquals(kotlin.math.sqrt(0.000102), r.uncertaintyDays, 1e-10)
        assertTrue(r.compatible)
        assertTrue(r.cycleUnambiguous)
    }

    @Test fun incompatiblePhase() {
        val r = TransitTimingPhysics.compareSectors(first, second.copy(epochBjd = 2460300.3), 3.0, 0.0001)
        assertFalse(r.compatible)
        assertTrue(r.cycleUnambiguous)
    }

    @Test fun accumulatedPeriodUncertaintyMakesCycleAmbiguous() {
        val r = TransitTimingPhysics.compareSectors(first, second, 3.0, 0.01)
        assertFalse(r.cycleUnambiguous)
        assertFalse(r.compatible)
    }

    @Test fun reverseOrderPreservesCompatibility() {
        val r = TransitTimingPhysics.compareSectors(second, first, 3.0, 0.0001)
        assertEquals(-100L, r.cycle)
        assertEquals(-0.002, r.residualDays, 1e-8)
        assertTrue(r.compatible)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateProduct() {
        TransitTimingPhysics.compareSectors(first, second.copy(productSha256 = "A".repeat(64)), 3.0, 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSameSector() {
        TransitTimingPhysics.compareSectors(first, second.copy(sector = 1), 3.0, 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownPeriodError() {
        TransitTimingPhysics.compareSectors(first, second, 3.0, Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidEpoch() {
        TransitTimingPhysics.compareSectors(first, second.copy(epochBjd = 1500.0), 3.0, 0.0001)
    }
}
