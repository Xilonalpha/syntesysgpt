package com.synthesis

import com.synthesis.engine.exoplanet.LinearEphemeris
import com.synthesis.engine.exoplanet.TransitTimingPhysics
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class HeldOutTransitTimingTest {
    private val epoch = 2460000.0
    private val fit = LinearEphemeris(0, epoch, 2.0, 0.001, 0.0, 1,
        emptyList(), 0.01, -0.000005)

    @Test fun compatibleHeldOutTransit() {
        val r = TransitTimingPhysics.compareHeldOut(fit, epoch + 20.0, 0.01)
        assertEquals(10L, r.cycle)
        assertTrue(r.compatible)
        assertTrue(r.cycleUnambiguous)
        assertEquals(sqrt(0.0002), r.uncertaintyDays, 1e-12)
    }

    @Test fun inconsistentPhase() {
        val r = TransitTimingPhysics.compareHeldOut(fit, epoch + 20.3, 0.01)
        assertFalse(r.compatible)
        assertTrue(r.cycleUnambiguous)
    }

    @Test fun longBaselineAmbiguity() {
        val r = TransitTimingPhysics.compareHeldOut(fit, epoch + 2000.0, 0.01)
        assertFalse(r.cycleUnambiguous)
        assertFalse(r.compatible)
    }

    @Test fun covarianceChangesPredictionUncertainty() {
        val a = TransitTimingPhysics.compareHeldOut(fit, epoch + 20, 0.01)
        val b = TransitTimingPhysics.compareHeldOut(fit.copy(epochPeriodCovariance = 0.0), epoch + 20, 0.01)
        assertTrue(a.uncertaintyDays < b.uncertaintyDays)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCovarianceRejected() {
        TransitTimingPhysics.compareHeldOut(fit.copy(epochPeriodCovariance = 0.001), epoch + 20, 0.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingUncertaintyRejected() {
        TransitTimingPhysics.compareHeldOut(fit, epoch + 20, Double.NaN)
    }

    @Test fun backwardPrediction() {
        val r = TransitTimingPhysics.compareHeldOut(fit, epoch - 20, 0.01)
        assertEquals(-10L, r.cycle)
        assertTrue(r.compatible)
        assertEquals(sqrt(0.0004), r.uncertaintyDays, 1e-12)
    }
}
