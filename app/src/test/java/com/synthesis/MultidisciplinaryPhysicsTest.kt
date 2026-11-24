package com.synthesis

import com.synthesis.engine.exoplanet.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class MultidisciplinaryPhysicsTest {
    @Test fun romanMeasuredMass() {
        val r = RomanMicrolensingEngine().measuredLensMass(0.8144,0.008144,0.1,0.001)
        assertEquals(1.0,r.solarMass,1e-10)
        assertEquals(sqrt(2.0)*0.01,r.standardErrorSolar,1e-10)
    }
    @Test(expected=IllegalArgumentException::class)
    fun romanRejectsZeroParallax() {
        RomanMicrolensingEngine().measuredLensMass(1.0,0.01,0.0,0.01)
    }
    @Test fun platoDepthUncertainty() {
        val r = PlatoAsteroseismologyEngine().propagateFractionalErrors(0.0,0.0,0.0,0.04)
        assertEquals(0.02,r.planetRadius,1e-12)
    }
    @Test fun platoFrequencyUncertainty() {
        val r = PlatoAsteroseismologyEngine().propagateFractionalErrors(0.0,0.01,0.0,0.0)
        assertEquals(0.04,r.mass,1e-12)
        assertEquals(0.02,r.radius,1e-12)
        assertEquals(0.02,r.density,1e-12)
    }
    @Test fun moonCircularOrbitHasZeroEccentricityHeating() {
        val r = ExomoonTidalOceanEngine().evaluateTidalScenario(1.898e27,1.989e30,7.78e11,1.82e6,4.22e8,0.0,0.002)
        assertEquals(0.0,r.heatFluxWm2,0.0)
        assertTrue(r.progradeLimitM > 4.22e8)
    }
    @Test fun moonHeatingScalesWithEccentricitySquared() {
        val e = ExomoonTidalOceanEngine()
        fun flux(x:Double) = e.evaluateTidalScenario(1.898e27,1.989e30,7.78e11,1.82e6,4.22e8,x,0.002).heatFluxWm2
        assertEquals(4.0,flux(0.02)/flux(0.01),1e-10)
    }
    @Test fun jwstRemovesLinearContinuum() {
        val bins = listOf(1.0,1.1,2.0,2.1,3.0,3.1).map { MeasuredSpectralBin(it,100+20*it,2.0) }
        val r = SpectralContrastPhysics.evaluateBracketed(bins,2.0..2.1,1.0..1.1,3.0..3.1)
        assertEquals(0.0,r.contrastPpm,1e-10)
        assertEquals(sqrt(3.0),r.errorPpm,1e-10)
    }
    @Test fun jwstRecoversInjectedFeature() {
        val bins = listOf(1.0,1.1,2.0,2.1,3.0,3.1).map { MeasuredSpectralBin(it,100+20*it+if(it in 2.0..2.1) 15.0 else 0.0,2.0) }
        assertEquals(15.0,SpectralContrastPhysics.evaluateBracketed(bins,2.0..2.1,1.0..1.1,3.0..3.1).contrastPpm,1e-10)
    }
    @Test fun ttvLinearOrbitHasZeroScatter() {
        val r = TtvInversionEngine().diagnoseTimingScatter((0..5).map { TransitTiming(it,2460000.0+2*it,0.001) })
        assertEquals(0.0,r.rmsMinutes,1e-8)
    }
    @Test fun ttvPerturbationIncreasesScatter() {
        val r = TtvInversionEngine().diagnoseTimingScatter((0..5).map { TransitTiming(it,2460000.0+2*it+if(it==3) 0.02 else 0.0,0.001) })
        assertTrue(r.reducedChiSquared > 1)
    }
}
