package com.synthesis

import com.synthesis.engine.exoplanet.OrbitalAliasPhysics
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class OrbitalAliasPhysicsTest {
    @Test fun enumeratesWithoutSelectingUniquePeriod() {
        val r = OrbitalAliasPhysics.enumerate(1683.15, 2496.15, 0.02, 0.03, 0.0, 100.0, 300.0)
        assertEquals((3..8).toList(), r.map { it.cycles })
        assertEquals(271.0, r.first().periodDays, 1e-9)
        assertEquals(sqrt(0.0013)/3, r.first().periodErrorDays, 1e-12)
    }
    @Test fun correlatedEpochErrors() {
        val r = OrbitalAliasPhysics.enumerate(100.0, 200.0, 0.1, 0.1, 0.5, 100.0, 100.0)
        assertEquals(0.1, r.single().periodErrorDays, 1e-12)
    }
    @Test fun noAliasInBounds() {
        assertTrue(OrbitalAliasPhysics.enumerate(100.0, 200.0, 0.1, 0.1, 0.0, 101.0, 200.0).isEmpty())
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsReversedEpochs() {
        OrbitalAliasPhysics.enumerate(200.0, 100.0, 0.1, 0.1, 0.0, 1.0, 100.0)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsExcessiveSearch() {
        OrbitalAliasPhysics.enumerate(100.0, 200.0, 0.1, 0.1, 0.0, 0.00001, 100.0)
    }
}
