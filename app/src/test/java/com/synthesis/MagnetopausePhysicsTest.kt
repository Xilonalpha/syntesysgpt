package com.synthesis

import com.synthesis.engine.exoplanet.SpaceWeatherMagnetosphereEngine
import org.junit.Assert.*
import org.junit.Test

class MagnetopausePhysicsTest {
    private val engine = SpaceWeatherMagnetosphereEngine()
    @Test fun earthLikeReference() {
        val r = engine.evaluateMeasuredPressureBalance(3.1e-5, 5.0, 400.0)
        assertEquals(1.338097538952, r.ramPressureNanoPa, 1e-9)
        assertEquals(8.1, r.standoffPlanetRadii, 0.2)
        assertTrue(r.balanceAboveSurface)
    }
    @Test fun densityScaling() {
        val a = engine.evaluateMeasuredPressureBalance(3.1e-5, 5.0, 400.0)
        val b = engine.evaluateMeasuredPressureBalance(3.1e-5, 320.0, 400.0)
        assertEquals(a.standoffPlanetRadii / 2, b.standoffPlanetRadii, 1e-10)
    }
    @Test fun weakFieldDoesNotEstablishExternalBalance() {
        assertFalse(engine.evaluateMeasuredPressureBalance(1e-9, 5.0, 400.0).balanceAboveSurface)
    }
    @Test(expected = IllegalArgumentException::class)
    fun invalidDensity() { engine.evaluateMeasuredPressureBalance(3.1e-5, 0.0, 400.0) }
    @Test(expected = IllegalArgumentException::class)
    fun invalidField() { engine.evaluateMeasuredPressureBalance(Double.NaN, 5.0, 400.0) }
}
