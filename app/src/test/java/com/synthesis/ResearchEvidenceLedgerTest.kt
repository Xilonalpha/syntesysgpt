package com.synthesis

import com.synthesis.engine.validation.ResearchEvidenceLedger as Ledger
import org.junit.Assert.*
import org.junit.Test

class ResearchEvidenceLedgerTest {
    private fun evidence(id: String = "a", hash: String = "a".repeat(64),
        outcome: Ledger.Outcome = Ledger.Outcome.SUPPORTS) =
        Ledger.Evidence("target","hypothesis",id,hash,"method",outcome)
    @Test fun repeatedAnalysisCountsOnce() {
        val e = evidence()
        val r = Ledger.summarize(listOf(e,e.copy(method="other")))
        assertEquals(1,r.supportingAcquisitions)
        assertEquals(1,r.distinctProducts)
    }
    @Test fun conflictingMethodsRemainInconclusive() {
        val r = Ledger.summarize(listOf(evidence(),evidence(outcome=Ledger.Outcome.CONTRADICTS)))
        assertEquals(1,r.inconclusiveAcquisitions)
        assertEquals(0,r.supportingAcquisitions)
    }
    @Test fun distinctAcquisitionsPreserved() {
        val r = Ledger.summarize(listOf(evidence(),evidence("b","b".repeat(64),Ledger.Outcome.CONTRADICTS)))
        assertEquals(2,r.distinctAcquisitions)
        assertEquals(1,r.contradictingAcquisitions)
    }
    @Test(expected=IllegalArgumentException::class)
    fun sameProductCannotMasqueradeAsNewAcquisition() {
        Ledger.summarize(listOf(evidence(),evidence("b","A".repeat(64))))
    }
    @Test(expected=IllegalArgumentException::class)
    fun mixedTargetsRejected() {
        Ledger.summarize(listOf(evidence(),evidence().copy(targetKey="other")))
    }
}
