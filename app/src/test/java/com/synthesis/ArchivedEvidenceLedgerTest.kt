package com.synthesis

import com.synthesis.service.ExperimentManifestStore
import com.synthesis.service.FitsEvidenceStore
import com.synthesis.engine.validation.ResearchEvidenceLedger.Outcome
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

class ArchivedEvidenceLedgerTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun repeatedSectorIsNotIndependentEvidence() {
        val evidence = FitsEvidenceStore(temp.newFolder("fits"))
        val store = ExperimentManifestStore(temp.newFolder("manifests"),evidence)
        val hash = evidence.save(byteArrayOf(1,2,3))
        val source = ExperimentManifestStore.Source("mast:TESS/product/test_lc.fits",hash,1,20)
        val first = store.save(123,listOf(source),"test")
        val second = store.save(123,listOf(source),"test2")
        val r = store.summarizeArchivedOutcomes("period",listOf(
            ExperimentManifestStore.ArchivedOutcome(first,1,"BLS",Outcome.SUPPORTS),
            ExperimentManifestStore.ArchivedOutcome(second,1,"timing",Outcome.CONTRADICTS)))
        assertEquals(1,r.distinctAcquisitions)
        assertEquals(1,r.inconclusiveAcquisitions)
    }
}
