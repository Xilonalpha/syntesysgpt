package com.synthesis

import com.synthesis.engine.cosmology.BlackHoleRelativityEngine
import com.synthesis.engine.cosmology.BigBangCosmologyEngine
import com.synthesis.engine.terra.TerraFrontierEngine
import com.synthesis.engine.spark.SynthesisSparkEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class ExtendedPhysicalModelsTest {
    @Test fun schwarzschildHorizon() {
        val r = BlackHoleRelativityEngine().horizonGeometry(1.0,0.0)
        assertEquals(2953.34,r.outerRadiusM,1.0)
        assertEquals(0.0,r.innerRadiusM,0.0)
    }
    @Test fun extremalKerr() {
        val r = BlackHoleRelativityEngine().horizonGeometry(1.0,1.0)
        assertEquals(r.outerRadiusM,r.innerRadiusM,1e-9)
    }
    @Test fun spinSignSymmetry() {
        val e = BlackHoleRelativityEngine()
        assertEquals(e.horizonGeometry(10.0,0.8).areaM2,e.horizonGeometry(10.0,-0.8).areaM2,1e-6)
    }
    @Test fun matterOnlyAge() {
        assertEquals(9.312,BigBangCosmologyEngine().flatLambdaAgeGyr(70.0,1.0),0.01)
    }
    @Test fun lambdaAge() {
        assertEquals(13.47,BigBangCosmologyEngine().flatLambdaAgeGyr(70.0,0.3),0.02)
    }
    @Test fun hydrostaticLinearDensity() {
        val e = TerraFrontierEngine()
        val p = e.integrateHydrostaticPressure(listOf(TerraFrontierEngine.DensitySample(0.0,1000.0),
            TerraFrontierEngine.DensitySample(100.0,1020.0)),10.0,0.0)
        assertEquals(1010000.0,p,1e-8)
    }
    @Test(expected=IllegalArgumentException::class)
    fun hydrostaticRejectsReversedDepth() {
        TerraFrontierEngine().integrateHydrostaticPressure(listOf(0.0,100.0,50.0).map {
            TerraFrontierEngine.DensitySample(it,1000.0) })
    }
    @Test fun foldedContrast() {
        val points = listOf(-0.4,-0.3,0.3,-0.01,0.0,0.01).map {
            SynthesisSparkEngine.WeightedFlux(it,if(kotlin.math.abs(it)<0.05) 0.98 else 1.0,0.003)
        }
        val r = SynthesisSparkEngine().measureFoldedContrast(points,0.05)
        assertEquals(0.02,r.depth,1e-10)
        assertEquals(0.003*sqrt(2.0/3),r.error,1e-10)
    }
}
