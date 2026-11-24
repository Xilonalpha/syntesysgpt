package com.synthesis

import com.synthesis.engine.exoplanet.TransitAsymmetryPhysics
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class TransitAsymmetryPhysicsTest {
    @Test fun symmetric() {
        val r = TransitAsymmetryPhysics.evaluate(2.0, 2.0, 0.1, 0.1, 0.0)
        assertEquals(0.0, r.signedAsymmetry, 1e-12)
        assertEquals(0.0, r.differenceSigma, 1e-12)
        assertEquals(sqrt(0.02)/4, r.asymmetryError, 1e-12)
    }
    @Test fun asymmetric() {
        val r = TransitAsymmetryPhysics.evaluate(3.0, 1.0, 0.2, 0.2, 0.0)
        assertEquals(0.5, r.signedAsymmetry, 1e-12)
        assertEquals(2/sqrt(0.08), r.differenceSigma, 1e-12)
        assertEquals(sqrt(0.00625), r.asymmetryError, 1e-12)
    }
    @Test fun correlated() {
        val r = TransitAsymmetryPhysics.evaluate(3.0, 1.0, 0.2, 0.2, 0.5)
        assertEquals(10.0, r.differenceSigma, 1e-10)
        assertEquals(sqrt(0.004375), r.asymmetryError, 1e-12)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownUncertainty() {
        TransitAsymmetryPhysics.evaluate(3.0, 1.0, Double.NaN, 0.2, 0.0)
    }
}
