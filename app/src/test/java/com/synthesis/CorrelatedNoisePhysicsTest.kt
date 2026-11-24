package com.synthesis

import com.synthesis.engine.exoplanet.CorrelatedNoisePhysics
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

class CorrelatedNoisePhysicsTest {
    private val times = List(1000) { it.toDouble() }

    @Test fun whiteNoiseBaseline() {
        val random = Random(175)
        val d = CorrelatedNoisePhysics.estimate(times, times.map { random.nextDouble(-1.0, 1.0) }, 1.0, 10)!!
        assertTrue(kotlin.math.abs(d.lagOneCorrelation) < 0.15)
        assertTrue(d.blockNoiseRatio in 0.7..1.3)
        assertEquals(100, d.completeBlocks)
    }

    @Test fun slowCorrelatedResiduals() {
        val d = CorrelatedNoisePhysics.estimate(times, times.map { sin(it * 0.02) }, 1.0, 10)!!
        assertTrue(d.lagOneCorrelation > 0.95)
        assertTrue(d.blockNoiseRatio > 2.5)
    }

    @Test fun excludesGapFromPairsAndBlocks() {
        val t = List(100) { it.toDouble() + if (it >= 45) 100.0 else 0.0 }
        val d = CorrelatedNoisePhysics.estimate(t, t.map { sin(it) }, 1.0, 10)!!
        assertEquals(98, d.adjacentPairs)
        assertEquals(9, d.completeBlocks)
    }

    @Test fun constantResidualsHaveNoDiagnostic() {
        assertNull(CorrelatedNoisePhysics.estimate(times, times.map { 0.0 }, 1.0, 10))
    }

    @Test fun insufficientCompleteBlocks() {
        assertNull(CorrelatedNoisePhysics.estimate(times.take(30), times.take(30).map { sin(it) }, 1.0, 10))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateTimes() {
        CorrelatedNoisePhysics.estimate(List(100) { 1.0 }, List(100) { sin(it.toDouble()) }, 1.0, 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonfiniteResiduals() {
        CorrelatedNoisePhysics.estimate(times, times.map { Double.NaN }, 1.0, 10)
    }
}
