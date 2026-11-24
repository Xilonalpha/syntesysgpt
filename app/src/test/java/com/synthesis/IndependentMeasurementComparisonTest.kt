package com.synthesis

import com.synthesis.engine.validation.IndependentMeasurementComparison as C
import org.junit.Assert.*
import org.junit.Test

class IndependentMeasurementComparisonTest {
    private val a = C.Input("o1","a1","s1","target:period","days",10.0,0.1)
    private val b = a.copy(observationId="o2", acquisitionId="a2", sourceId="s2")

    @Test fun consistent() {
        val r = C.compare(a,b.copy(value=10.1),true)
        assertEquals(C.Status.CONSISTENT,r.status)
        assertEquals(1.0/kotlin.math.sqrt(2.0),r.standardizedDifference!!,1e-10)
    }
    @Test fun discrepant() {
        assertEquals(C.Status.DISCREPANT,C.compare(a,b.copy(value=11.0),true).status)
    }
    @Test fun duplicateAcquisition() {
        assertEquals(C.Status.INSUFFICIENT_PROVENANCE,C.compare(a,b.copy(acquisitionId="a1"),true).status)
    }
    @Test fun unestablishedIndependence() {
        assertEquals(C.Status.INSUFFICIENT_PROVENANCE,C.compare(a,b,false).status)
    }
    @Test fun differentUnits() {
        assertEquals(C.Status.INCOMPARABLE,C.compare(a,b.copy(unit="hours"),true).status)
    }
    @Test fun invalidUncertainty() {
        assertEquals(C.Status.INCOMPARABLE,C.compare(a,b.copy(standardUncertainty=0.0),true).status)
    }
    @Test fun differentQuantity() {
        assertEquals(C.Status.INCOMPARABLE,C.compare(a,b.copy(quantityKey="other:period"),true).status)
    }
}
