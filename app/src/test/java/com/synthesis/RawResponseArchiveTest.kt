package com.synthesis
import com.synthesis.service.RawResponseArchive
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
class RawResponseArchiveTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun roundTripAndDeduplication() {
        val store = RawResponseArchive(folder.root)
        val bytes = byteArrayOf(1,2,3)
        val hash = store.save(bytes)
        assertEquals(hash,store.save(bytes))
        assertArrayEquals(bytes,store.readVerified(hash))
        assertEquals(1,folder.root.listFiles()!!.size)
    }
    @Test(expected=IllegalStateException::class)
    fun rejectsTampering() {
        val store = RawResponseArchive(folder.root)
        val hash = store.save(byteArrayOf(1,2,3))
        File(folder.root,"$hash.raw").writeBytes(byteArrayOf(3,2,1))
        store.readVerified(hash)
    }
    @Test(expected=IllegalStateException::class)
    fun enforcesQuota() {
        val store = RawResponseArchive(folder.root,3)
        store.save(byteArrayOf(1,2,3))
        store.save(byteArrayOf(4))
    }
    @Test(expected=IllegalArgumentException::class)
    fun rejectsInvalidHash() {
        RawResponseArchive(folder.root).readVerified("../outside")
    }
}
