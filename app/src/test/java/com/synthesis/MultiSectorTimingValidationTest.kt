package com.synthesis

import com.synthesis.engine.exoplanet.MultiSectorTimingValidation
import com.synthesis.engine.exoplanet.TransitTiming
import org.junit.Assert.*
import org.junit.Test

class MultiSectorTimingValidationTest {
    private val epoch = 2460000.0
    private fun timing(n: Int, offset: Double = 0.0) = TransitTiming(n, epoch + 2.0*n + offset, 0.001)
    private val training = MultiSectorTimingValidation.SectorTimings(1, "a".repeat(64), (0..4).map { timing(it) })
    private val held = MultiSectorTimingValidation.SectorTimings(2, "b".repeat(64), listOf(timing(10), timing(11)))

    @Test fun compatiblePrediction() {
        val r = MultiSectorTimingValidation.evaluate(training, held)
        assertTrue(r.allCompatible)
        assertEquals(2.0, r.ephemeris.periodDays, 1e-10)
        assertEquals(listOf(10L, 11L), r.comparisons.map { it.cycle })
    }
    @Test fun shiftedTransitRejected() {
        assertFalse(MultiSectorTimingValidation.evaluate(training, held.copy(timings = listOf(timing(10, 0.2), timing(11)))).allCompatible)
    }
    @Test fun ambiguousLongBaseline() {
        assertFalse(MultiSectorTimingValidation.evaluate(training, held.copy(timings = listOf(timing(10000), timing(10001)))).allCompatible)
    }
    @Test(expected = IllegalArgumentException::class)
    fun duplicateProductRejected() {
        MultiSectorTimingValidation.evaluate(training, held.copy(productSha256 = training.productSha256))
    }
    @Test(expected = IllegalArgumentException::class)
    fun sameSectorRejected() {
        MultiSectorTimingValidation.evaluate(training, held.copy(sector = training.sector))
    }
    @Test(expected = IllegalArgumentException::class)
    fun overlappingTimeRejected() {
        MultiSectorTimingValidation.evaluate(training, held.copy(timings = listOf(timing(3), timing(10))))
    }
    @Test(expected = IllegalArgumentException::class)
    fun insufficientHeldOutEventsRejected() {
        MultiSectorTimingValidation.evaluate(training, held.copy(timings = listOf(timing(10))))
    }
    @Test fun duplicatePredictedCycleRejected() {
        assertFalse(MultiSectorTimingValidation.evaluate(training, held.copy(timings = listOf(timing(10), TransitTiming(11, epoch+20.0001, 0.001)))).allCompatible)
    }
}
