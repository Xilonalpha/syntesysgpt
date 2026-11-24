package com.synthesis

import com.synthesis.service.ExperimentManifestStore
import com.synthesis.service.ExperimentManifestStore.ProvenanceComparison
import com.synthesis.service.FitsEvidenceStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class ExperimentProvenanceTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun verifiedProvenanceClassification() {
        val root = temp.newFolder()
        val evidence = FitsEvidenceStore(root.resolve("fits"))
        val store = ExperimentManifestStore(root.resolve("manifests"), evidence)
        val a = ExperimentManifestStore.Source("mast:TESS/product/a_lc.fits", evidence.save(byteArrayOf(1,2,3)), 1, 8)
        val b = ExperimentManifestStore.Source("mast:TESS/product/b_lc.fits", evidence.save(byteArrayOf(4,5,6)), 2, 8)
        fun save(s: ExperimentManifestStore.Source, tic: Long = 123L) = store.save(tic, listOf(s), "BLS-test")
        val first = save(a)
        assertEquals(ProvenanceComparison.SHARED_PRODUCT, store.compareProvenance(first, save(a)))
        assertEquals(ProvenanceComparison.SHARED_PRODUCT, store.compareProvenance(first, save(b.copy(sha256 = a.sha256))))
        assertEquals(ProvenanceComparison.SHARED_PRODUCT, store.compareProvenance(first, save(b.copy(uri = a.uri))))
        assertEquals(ProvenanceComparison.SHARED_SECTOR, store.compareProvenance(first, save(b.copy(sector = 1))))
        val second = save(b)
        assertEquals(ProvenanceComparison.DISTINCT_ARCHIVED_PRODUCTS, store.compareProvenance(first, second))
        assertEquals(ProvenanceComparison.DISTINCT_ARCHIVED_PRODUCTS, store.compareProvenance(second, first))
        assertEquals(ProvenanceComparison.DIFFERENT_TARGET, store.compareProvenance(first, save(b, 456L)))
        root.resolve("fits/${b.sha256}.fits").writeBytes(byteArrayOf(9))
        assertTrue(runCatching { store.compareProvenance(first, second) }.isFailure)
    }
}
