package com.synthesis

import com.synthesis.domain.model.*
import com.synthesis.engine.discovery.PatternDetector
import com.synthesis.engine.normalizer.DataNormalizer
import com.synthesis.engine.astrometry.AstrometryEngine
import org.junit.Assert.*
import org.junit.Test

class RobustPatternRegressionTest {
    private val detector = PatternDetector(DataNormalizer())
    private fun obs(i: Int, value: Double, source: String = "s", investigation: String = "a") =
        Observation("o$i", investigation, source, source, "target", i.toLong(), null,
            Measurement(value, "m"), "", "", 0.1f, ObservationType.ASTRONOMICAL)

    private fun baseline() = (0 until 20).map { obs(it, (it % 5).toDouble()) }

    @Test fun detectsInjectedOutlierWithoutClaimingSignificance() {
        val flags = detector.detectHiddenPatterns(baseline() + obs(20, 100.0))
        assertEquals(1, flags.size)
        assertEquals(listOf("o20"), flags.single().involvedObservations)
        assertEquals(0f, flags.single().significance, 0f)
    }

    @Test fun ordinaryBaselineHasNoFlags() {
        assertTrue(detector.detectHiddenPatterns(baseline()).isEmpty())
    }

    @Test fun separateSourceIsNotCompared() {
        assertTrue(detector.detectHiddenPatterns(baseline() + obs(20, 100.0, "other")).isEmpty())
    }

    @Test fun zeroMadIsNotInventedNoise() {
        assertTrue(detector.detectHiddenPatterns((0 until 20).map { obs(it, 1.0) } + obs(20, 100.0)).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateObservationRejected() {
        detector.detectHiddenPatterns(baseline() + obs(0, 100.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun mixedInvestigationsRejected() {
        detector.detectHiddenPatterns(baseline() + obs(20, 100.0, investigation = "b"))
    }

    @Test fun sexagesimalCarry() {
        val engine = AstrometryEngine()
        assertEquals("00h 00m 00.00s", engine.formatSexagesimalRa(359.999999))
        assertEquals("+90° 00' 00.0\"", engine.formatSexagesimalDec(89.999999))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidLatitudeRejected() {
        DataNormalizer().calculateDistance(GeoLocation(91.0, 0.0), GeoLocation(0.0, 0.0))
    }
}
