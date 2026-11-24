package com.synthesis

import com.synthesis.engine.exoplanet.*
import org.junit.Assert.*
import org.junit.Test

class AtmosphericReactionPhysicsTest {
    @Test fun standardState() {
        val r = AtmosphericReactionPhysics.evaluate(300.0, -10000.0,
            listOf(ReactionSpecies(-1.0, 1.0), ReactionSpecies(1.0, 1.0)))
        assertEquals(0.0, r.logReactionQuotient, 1e-12)
        assertEquals(-10000.0, r.gibbsJPerMol, 1e-9)
    }
    @Test fun nonStandardState() {
        val r = AtmosphericReactionPhysics.evaluate(300.0, 0.0,
            listOf(ReactionSpecies(-1.0, 0.1), ReactionSpecies(1.0, 1.0)))
        assertEquals(5743.4273, r.gibbsJPerMol, 0.01)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroActivity() {
        AtmosphericReactionPhysics.evaluate(300.0, 0.0,
            listOf(ReactionSpecies(-1.0, 0.0), ReactionSpecies(1.0, 1.0)))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingProducts() {
        AtmosphericReactionPhysics.evaluate(300.0, 0.0,
            listOf(ReactionSpecies(-1.0, 1.0), ReactionSpecies(-2.0, 1.0)))
    }
}
