package com.synthesis

import com.synthesis.engine.exoplanet.BayesianUncertaintyEngine
import com.synthesis.service.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class SingleDipParserIntegrationTest {
    private val parser = PhotometricFileParser(FitsImageParser(), PeriodicTransitAnalysisEngine(BayesianUncertaintyEngine()))
    private fun csv(depth: Double, gap: Boolean = false, spike: Boolean = false): String = buildString {
        append("BJD_TDB,Relative_Flux,Flux_Error\n")
        for (i in 0..20) {
            val t = 2460000.0 + i * 0.002 + if (gap && i >= 10) 0.04 else 0.0
            val dip = if (if (spike) i == 10 else i in 9..11) depth else 0.0
            append("$t,${1.0 - dip + 0.0002 * sin(i.toDouble())},0.001\n")
        }
    }

    @Test fun isolatedDipRemainsPreliminary() {
        val result = parser.parsePhotometryData(csv(0.015), 1.0)
        val dip = requireNotNull(result.preliminarySingleDip)
        assertTrue(dip.depthPpm in 14000.0..16000.0)
        assertTrue(dip.localSnr >= 7)
        assertFalse(requireNotNull(result.periodicTransitResult).isDetected)
        assertTrue(result.measuredRadiusEarth.isNaN())
    }

    @Test fun rejectsNoiseOnly() {
        assertNull(parser.parsePhotometryData(csv(0.0)).preliminarySingleDip)
    }

    @Test fun rejectsSingleOutlier() {
        assertNull(parser.parsePhotometryData(csv(0.015, spike = true)).preliminarySingleDip)
    }

    @Test fun rejectsGapAcrossEvent() {
        assertNull(parser.parsePhotometryData(csv(0.015, gap = true)).preliminarySingleDip)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMismatchedUncertainty() {
        parser.parsePhotometryData(csv(0.015).replace("Relative_Flux,Flux_Error", "rel_flux_T1,rel_flux_err_T2"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownTimeScale() {
        parser.parsePhotometryData(csv(0.015).replace("BJD_TDB", "JD"))
    }
}
