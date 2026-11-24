package com.synthesis
import com.synthesis.engine.exoplanet.TransitDipScannerUtility
import com.synthesis.domain.model.xilon.MastPhotometryPoint
import org.junit.Test
import org.junit.Assert.*
class TransitDipScannerUtilityTest {
    @Test fun phaseOnlyDataCannotEstablishDetection() {
        val points = List(100) { MastPhotometryPoint(it / 100.0, 1.0, -0.01) }
        val report = TransitDipScannerUtility().scanForRecurringDips("123", points, 5.6)
        assertTrue(report.allDips.isEmpty())
        assertNull(report.recurrencePeriodCandidateDays)
        assertNull(report.oddEvenConsistencySigma)
        assertTrue(report.noiseFloorPpm.isNaN())
    }
}
