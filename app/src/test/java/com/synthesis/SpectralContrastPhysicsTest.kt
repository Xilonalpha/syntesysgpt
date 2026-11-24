package com.synthesis

import com.synthesis.engine.exoplanet.*
import org.junit.Assert.*
import org.junit.Test

class SpectralContrastPhysicsTest {
    private fun bins(depth: Double) = listOf(
        MeasuredSpectralBin(4.2, depth, 20.0),
        MeasuredSpectralBin(4.3, depth, 20.0),
        MeasuredSpectralBin(3.7, 1000.0, 20.0),
        MeasuredSpectralBin(3.8, 1000.0, 20.0))
    @Test fun positiveContrast() {
        val r = SpectralContrastPhysics.evaluate(bins(1100.0), 4.1..4.4, 3.6..3.9)
        assertEquals(100.0, r.contrastPpm, 1e-9)
        assertEquals(20.0, r.errorPpm, 1e-9)
        assertEquals(5.0, r.signedSigma, 1e-9)
    }
    @Test fun flatSpectrum() {
        assertEquals(0.0, SpectralContrastPhysics.evaluate(bins(1000.0), 4.1..4.4, 3.6..3.9).signedSigma, 1e-9)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsOverlap() {
        SpectralContrastPhysics.evaluate(bins(1100.0), 3.7..4.4, 3.6..3.9)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingCoverage() {
        SpectralContrastPhysics.evaluate(bins(1100.0), 5.0..6.0, 3.6..3.9)
    }
}
