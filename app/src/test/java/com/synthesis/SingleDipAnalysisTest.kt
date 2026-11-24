package com.synthesis

import com.synthesis.domain.model.exoplanet.PhotometricDataPoint
import com.synthesis.service.SingleDipAnalysis
import org.junit.Assert.*
import org.junit.Test

class SingleDipAnalysisTest {
    private val epoch = 2460000.0
    private fun points(depth: Double) = (0..8).map { i ->
        PhotometricDataPoint(i*14.4, epoch+i*0.01, if (i in 3..5) 1-depth else 1.0, 0.001)
    }

    @Test fun estimatesSyntheticDip() {
        val result = requireNotNull(SingleDipAnalysis.estimate(points(0.01), epoch+0.025, epoch+0.055))
        assertEquals(10000.0, result.depthPpm, 0.01)
        assertEquals(28.8, result.sampledSpanMinutes, 0.001)
        assertTrue(result.depthErrorPpm > 0)
        assertTrue(result.localSnr > 5)
    }

    @Test fun rejectsFlatSignal() {
        assertNull(SingleDipAnalysis.estimate(points(0.0), epoch+0.025, epoch+0.055))
    }

    @Test fun rejectsMissingBaseline() {
        assertNull(SingleDipAnalysis.estimate(points(0.01).drop(2), epoch+0.025, epoch+0.055))
    }
}
