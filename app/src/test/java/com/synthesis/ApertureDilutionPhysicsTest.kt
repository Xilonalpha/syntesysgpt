package com.synthesis

import com.synthesis.engine.exoplanet.ApertureDilutionPhysics
import org.junit.Assert.*
import org.junit.Test

class ApertureDilutionPhysicsTest {
    @Test fun propagatesIndependentErrors() {
        val r = ApertureDilutionPhysics.estimate(1000.0, 10.0, 100.0, 2.0, 50.0, 3.0)
        assertEquals(1.5, r.correctionFactor, 1e-10)
        assertEquals(1500.0, r.depthPpm, 1e-8)
        assertEquals(kotlin.math.sqrt(1225.0), r.depthErrorPpm, 1e-8)
    }
    @Test fun zeroContaminationPreservesDepth() {
        val r = ApertureDilutionPhysics.estimate(1000.0, 10.0, 100.0, 2.0, 0.0, 0.0)
        assertEquals(1000.0, r.depthPpm, 1e-8)
        assertEquals(10.0, r.depthErrorPpm, 1e-8)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsImpossibleDepth() {
        ApertureDilutionPhysics.estimate(900000.0, 10.0, 100.0, 2.0, 50.0, 3.0)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingFlux() {
        ApertureDilutionPhysics.estimate(1000.0, 10.0, Double.NaN, 2.0, 50.0, 3.0)
    }
}
