package com.synthesis

import com.synthesis.engine.discovery.PlanetResearchPlanner as Planner
import org.junit.Assert.*
import org.junit.Test

class PlanetResearchPlannerTest {
    private val base = Planner.Input(1,1,true,false,false,false,false,false)
    @Test fun missingInputsRequireAcquisition() {
        assertEquals(listOf(Planner.Action.ACQUIRE_PHOTOMETRY),
            Planner.plan(base.copy(verifiedProducts=0,distinctSectors=0,preliminarySignal=false)))
    }
    @Test fun candidateNeedsCatalogNoiseAndMoreSectors() {
        val actions = Planner.plan(base)
        assertTrue(actions.containsAll(listOf(Planner.Action.CHECK_CATALOGS,
            Planner.Action.ASSESS_CORRELATED_NOISE,Planner.Action.ACQUIRE_MORE_SECTORS)))
    }
    @Test fun multisectorCandidateNeedsTiming() {
        assertTrue(Planner.Action.CHECK_TIMING in Planner.plan(base.copy(verifiedProducts=2,distinctSectors=2)))
    }
    @Test fun evenCompletedScreenRequiresIndependentFollowUp() {
        assertEquals(listOf(Planner.Action.INDEPENDENT_FOLLOW_UP),
            Planner.plan(Planner.Input(2,2,true,true,true,true,true,true)))
    }
    @Test(expected=IllegalArgumentException::class)
    fun impossibleSectorCountRejected() { Planner.plan(base.copy(distinctSectors=2)) }
}
