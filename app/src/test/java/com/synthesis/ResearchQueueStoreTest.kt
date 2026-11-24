package com.synthesis

import com.synthesis.service.ResearchQueueStore
import com.synthesis.engine.discovery.ResearchWorkQueue.Candidate
import com.synthesis.engine.discovery.PlanetResearchPlanner.Action
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class ResearchQueueStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val item = Candidate("tic:123",listOf(Action.CHECK_CATALOGS),1,100)
    @Test fun roundTrip() {
        val file = File(temp.root,"queue")
        ResearchQueueStore(file).save(listOf(item))
        assertEquals(listOf(item),ResearchQueueStore(file).load())
    }
    @Test fun replaceWithEmpty() {
        val store = ResearchQueueStore(File(temp.root,"queue"))
        store.save(listOf(item))
        store.save(emptyList())
        assertTrue(store.load().isEmpty())
    }
    @Test fun missingQueueIsEmpty() {
        assertTrue(ResearchQueueStore(File(temp.root,"missing")).load().isEmpty())
    }
    @Test(expected=IllegalStateException::class)
    fun corruptedQueueRejected() {
        val file = File(temp.root,"queue")
        val store = ResearchQueueStore(file)
        store.save(listOf(item))
        file.appendText("tampered")
        store.load()
    }
}
