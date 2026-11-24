package com.synthesis

import com.synthesis.domain.model.exoplanet.PhaseFoldedPoint
import com.synthesis.engine.exoplanet.IndividualTransitTiming
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class IndividualTransitTimingTest {
    private val epoch = 2460000.0
    private val period = 2.0
    private val duration = 0.2
    private fun samples(cycle: Long = 0, shift: Double = 0.025, flat: Boolean = false): List<PhaseFoldedPoint> =
        (-30..30).map { i ->
            val t = i * 0.01
            val inside = abs(t - shift) < duration / 2
            PhaseFoldedPoint(t / period, if (inside && !flat) 0.98 else 1.0,
                0.001, t * 1440, epoch + cycle * period + t, cycle, inside)
        }

    @Test fun recoversInjectedTiming() {
        val r = IndividualTransitTiming.estimate(samples(), period, epoch, duration).single()
        assertEquals(epoch + 0.025, r.epochDays, 0.011)
        assertTrue(r.errorDays.isFinite() && r.errorDays >= 0.0049)
    }

    @Test fun recoversMultipleCycles() {
        val r = IndividualTransitTiming.estimate(samples(2) + samples(0) + samples(1), period, epoch, duration)
        assertEquals(listOf(0, 1, 2), r.map { it.cycle })
    }

    @Test fun rejectsFlatFlux() {
        assertTrue(IndividualTransitTiming.estimate(samples(flat = true), period, epoch, duration).isEmpty())
    }

    @Test fun rejectsSparseCoverage() {
        assertTrue(IndividualTransitTiming.estimate(samples().take(8), period, epoch, duration).isEmpty())
    }

    @Test fun rejectsGap() {
        val points = samples().filter { abs(it.bjd - epoch) > 0.04 }
        assertTrue(IndividualTransitTiming.estimate(points, period, epoch, duration).isEmpty())
    }

    @Test fun rejectsDuplicateTimestamp() {
        val p = samples()
        assertTrue(IndividualTransitTiming.estimate(p + p[20], period, epoch, duration).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDuration() {
        IndividualTransitTiming.estimate(samples(), period, epoch, Double.NaN)
    }
}
