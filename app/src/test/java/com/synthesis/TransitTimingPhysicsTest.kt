package com.synthesis

import com.synthesis.engine.exoplanet.*
import org.junit.Assert.*
import org.junit.Test

class TransitTimingPhysicsTest {
    @Test fun constantPeriod() {
        val r = TransitTimingPhysics.fit((0..4).map { TransitTiming(it, 2460000.0+2.0*it, 0.01) })
        assertEquals(2.0, r.periodDays, 1e-10)
        assertEquals(0.01/kotlin.math.sqrt(10.0), r.periodErrorDays, 1e-10)
        assertEquals(0.0, r.chiSquared, 1e-10)
        assertEquals(3, r.degreesOfFreedom)
    }
    @Test fun nonLinearTiming() {
        val r = TransitTimingPhysics.fit(listOf(
            TransitTiming(0, 100.0, 0.01), TransitTiming(1, 102.03, 0.01),
            TransitTiming(2, 104.0, 0.01)))
        assertEquals(2.0, r.periodDays, 1e-10)
        assertEquals(6.0, r.chiSquared, 1e-8)
        assertEquals(28.8, r.residuals[1].residualMinutes, 1e-8)
    }
    @Test(expected = IllegalArgumentException::class)
    fun duplicateCycle() {
        TransitTimingPhysics.fit(listOf(TransitTiming(0, 100.0, 0.01),
            TransitTiming(0, 102.0, 0.01), TransitTiming(2, 104.0, 0.01)))
    }
    @Test(expected = IllegalArgumentException::class)
    fun unknownError() {
        TransitTimingPhysics.fit(listOf(TransitTiming(0, 100.0, 0.01),
            TransitTiming(1, 102.0, Double.NaN), TransitTiming(2, 104.0, 0.01)))
    }
}
