package com.synthesis

import com.synthesis.engine.discovery.ResearchWorkQueue as Queue
import com.synthesis.engine.discovery.PlanetResearchPlanner.Action
import org.junit.Assert.*
import org.junit.Test

class ResearchWorkQueueTest {
    private fun item(id: String, attempts: Int = 0, time: Long = 0) =
        Queue.Candidate(id,listOf(Action.CHECK_CATALOGS,Action.CHECK_TIMING),attempts,time)
    @Test fun freshWorkFirst() {
        assertEquals("fresh",Queue.next(listOf(item("retry",1),item("fresh",0,100)),1).single().candidateId)
    }
    @Test fun oldestFirst() {
        assertEquals("old",Queue.next(listOf(item("new",0,2),item("old",0,1)),1).single().candidateId)
    }
    @Test fun exhaustedWorkExcluded() { assertTrue(Queue.next(listOf(item("x",3))).isEmpty()) }
    @Test fun firstActionPreserved() { assertEquals(Action.CHECK_CATALOGS,Queue.next(listOf(item("x"))).single().action) }
    @Test(expected=IllegalArgumentException::class)
    fun duplicateIdsRejected() { Queue.next(listOf(item("x"),item("x"))) }
}
