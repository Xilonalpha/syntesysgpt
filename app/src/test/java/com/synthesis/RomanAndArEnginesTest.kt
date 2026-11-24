package com.synthesis

import android.content.Context
import com.synthesis.engine.exoplanet.RomanMicrolensingEngine
import com.synthesis.service.ArSkyTrackerService
import com.synthesis.service.DeviceSkyOrientation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class RomanAndArEnginesTest {

    private lateinit var romanEngine: RomanMicrolensingEngine

    @Before
    fun setUp() {
        romanEngine = RomanMicrolensingEngine()
    }

    @Test
    fun testPaczynskiMagnificationAnalyticValues() {
        // At u = 1.0, A(1) = (1 + 2) / (1 * sqrt(1 + 4)) = 3 / sqrt(5) ~ 1.34164
        val a1 = romanEngine.paczynskiMagnification(1.0)
        assertEquals(1.34164, a1, 0.001)

        // As u -> infinity, A(u) -> 1.0
        val aFar = romanEngine.paczynskiMagnification(10.0)
        assertTrue(aFar > 1.0 && aFar < 1.05)

        // At small impact parameter u = 0.05, A >> 1
        val aClose = romanEngine.paczynskiMagnification(0.05)
        assertTrue("Magnification for small u must exceed 15x", aClose > 15.0)
    }

    private fun syntheticEvent(binary: Boolean) = com.synthesis.domain.model.exoplanet.MicrolensingEvent(
        eventId = "SYNTHETIC-TEST", targetName = "Synthetic fixture",
        raDeg = 270.0, decDeg = -28.0, sourceDistanceKpc = 8.0, lensDistanceKpc = 4.0,
        impactParameterU0 = 0.1, einsteinCrossingTimeDays = if (binary) 34.0 else 0.1,
        peakMagnificationA0 = romanEngine.paczynskiMagnification(0.1),
        isFreeFloatingPlanet = !binary, planetMassEarth = 1.0,
        massRatioQ = if (binary) 0.001 else null, separationS = if (binary) 1.4 else null,
        lensType = "Synthetic", surveyOrigin = "Unit test", discoveryConfidencePercent = 0.0,
        description = "Not an observation"
    )

    @Test
    fun testRomanRoguePlanetInversion() {
        assertTrue(romanEngine.getBenchmarkEvents().isEmpty())
        val event = syntheticEvent(false)
        val result = romanEngine.invertMicrolensingEvent(event)
        val expected = Math.pow(6.5 * 0.1 / 365.25, 2.0) / (8.144 * 0.125) * 333000.0
        assertEquals(expected, result.inferredMassEarth, 0.0051)
        assertEquals(101, result.lightCurve.size)
        assertTrue(result.astrophysicalSummary.contains("Conditional"))
    }

    @Test
    fun testRomanColdJupiterBinaryCausticLightCurve() {
        val event = syntheticEvent(true)
        val result = romanEngine.invertMicrolensingEvent(event)
        val expected = Math.pow(6.5 * 34.0 / 365.25, 2.0) / (8.144 * 0.125) * (0.001 / 1.001) * 333000.0
        assertEquals(expected, result.inferredMassEarth, 0.0051)
        assertFalse(result.isFreeFloating)
        assertEquals(101, result.lightCurve.size)
        assertTrue(result.astrophysicalSummary.contains("no planet confirmation or binary-lens fit"))
        assertTrue(result.lightCurve.all { !it.isCausticCrossing && it.planetaryAnomalyDeltaA == 0.0 })
        assertTrue(result.lightCurve.zipWithNext().all { (a, b) -> b.timeDays > a.timeDays })
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRomanRejectsInvalidDistance() {
        romanEngine.invertMicrolensingEvent(syntheticEvent(false).copy(lensDistanceKpc = 9.0))
    }

    @Test
    fun testHorizontalCoordinateConversion() {
        // Trappist-1: RA = 346.62 deg, Dec = -5.04 deg
        // Observer at Bucharest (lat = 44.4 deg, lon = 26.1 deg)
        val (alt, az) = ArSkyTrackerService.computeHorizontalCoordinates(
            raDeg = 346.622,
            decDeg = -5.041,
            latDeg = 44.4268,
            lonDeg = 26.1025,
            epochMillis = 1775000000000L
        )

        assertTrue("Altitude must be within [-90, +90]", alt in -90.0..90.0)
        assertTrue("Azimuth must be within [0, 360]", az in 0.0..360.0)
    }

    @Test
    fun testArTargetScreenProjectionAlignment() {
        // Find coords for Trappist-1
        val (alt, az) = ArSkyTrackerService.computeHorizontalCoordinates(
            raDeg = 346.622,
            decDeg = -5.041,
            latDeg = 44.4268,
            lonDeg = 26.1025
        )

        // Point device exactly towards target Azimuth and Altitude
        val alignedOrientation = DeviceSkyOrientation(
            compassAzimuthDeg = az,
            altitudePitchDeg = alt,
            sensorActive = true,
            observerLatitude = 44.4268,
            observerLongitude = 26.1025
        )

        val projected = ArSkyTrackerService.computeProjectedTargets(alignedOrientation)
        assertTrue("No unverified catalog targets projected", projected.isEmpty())
    }
}
