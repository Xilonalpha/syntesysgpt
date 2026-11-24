package com.synthesis

import com.synthesis.service.*
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File

class ResearchReportStoreTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun reportRoundTripAndTamperDetection() {
        val fits = FitsEvidenceStore(temp.newFolder("fits"))
        val manifests = ExperimentManifestStore(temp.newFolder("manifests"),fits)
        val id = manifests.save(123,listOf(ExperimentManifestStore.Source(
            "mast:TESS/product/test_lc.fits",fits.save(byteArrayOf(1,2,3)),1,20)),"test")
        val root = temp.newFolder("reports")
        val store = ResearchReportStore(root,manifests)
        val hash = store.save(id,"conditional result")
        assertEquals(hash,store.save(id,"conditional result"))
        assertEquals("experiment=$id\nconditional result",store.readVerified(hash))
        File(root,"$hash.report").appendText("changed")
        try { store.readVerified(hash); fail("Corrupt report accepted") }
        catch (_: IllegalStateException) {}
    }
}
