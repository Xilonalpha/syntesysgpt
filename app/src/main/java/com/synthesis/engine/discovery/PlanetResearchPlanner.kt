package com.synthesis.engine.discovery

/** Plans follow-up work; does not infer planet existence or calibrated probability. */
object PlanetResearchPlanner {
    enum class Action { ACQUIRE_PHOTOMETRY, SEARCH_TRANSITS, CHECK_CATALOGS,
        ASSESS_CORRELATED_NOISE, CHECK_FALSE_POSITIVES, ACQUIRE_MORE_SECTORS,
        CHECK_TIMING, CHECK_CENTROID_AND_BLEND, INDEPENDENT_FOLLOW_UP }
    data class Input(val verifiedProducts: Int, val distinctSectors: Int,
        val preliminarySignal: Boolean, val catalogChecked: Boolean,
        val noiseAssessed: Boolean, val preliminaryVettingPassed: Boolean,
        val timingCompatible: Boolean, val centroidAndBlendChecked: Boolean)
    fun plan(input: Input): List<Action> {
        require(input.verifiedProducts >= 0 && input.distinctSectors in 0..input.verifiedProducts)
        require(!input.preliminarySignal || input.verifiedProducts > 0)
        if (input.verifiedProducts == 0) return listOf(Action.ACQUIRE_PHOTOMETRY)
        if (!input.preliminarySignal) return listOf(Action.SEARCH_TRANSITS)
        return buildList {
            if (!input.catalogChecked) add(Action.CHECK_CATALOGS)
            if (!input.noiseAssessed) add(Action.ASSESS_CORRELATED_NOISE)
            if (!input.preliminaryVettingPassed) add(Action.CHECK_FALSE_POSITIVES)
            if (input.distinctSectors < 2) add(Action.ACQUIRE_MORE_SECTORS)
            else if (!input.timingCompatible) add(Action.CHECK_TIMING)
            if (!input.centroidAndBlendChecked) add(Action.CHECK_CENTROID_AND_BLEND)
            add(Action.INDEPENDENT_FOLLOW_UP)
        }
    }
}
