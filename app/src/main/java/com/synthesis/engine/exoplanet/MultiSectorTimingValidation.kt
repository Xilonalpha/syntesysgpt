package com.synthesis.engine.exoplanet

/** Conditional validation using a training-sector ephemeris and held-out sector timings.
 * Caller must verify product provenance and establish the cycle assignment independently
 * of the held-out flux. This is not a calibrated planet confirmation.
 */
object MultiSectorTimingValidation {
    data class SectorTimings(val sector: Int, val productSha256: String, val timings: List<TransitTiming>)
    data class Result(val trainingSector: Int, val heldOutSector: Int,
        val ephemeris: LinearEphemeris, val comparisons: List<TransitTimingPhysics.PhaseComparison>,
        val allCompatible: Boolean)

    fun evaluate(training: SectorTimings, heldOut: SectorTimings): Result {
        require(training.sector > 0 && heldOut.sector > 0 && training.sector != heldOut.sector)
        require(listOf(training, heldOut).all { it.productSha256.matches(Regex("[0-9a-fA-F]{64}")) })
        require(!training.productSha256.equals(heldOut.productSha256, ignoreCase = true))
        require(training.timings.size >= 3 && heldOut.timings.size >= 2)
        require(heldOut.timings.map { it.cycle }.distinct().size == heldOut.timings.size)
        require((training.timings + heldOut.timings).all { it.epochDays.isFinite() && it.epochDays > 2400000 &&
            it.errorDays.isFinite() && it.errorDays > 0 })
        val last = training.timings.maxOf { it.epochDays }
        require(heldOut.timings.all { it.epochDays > last })
        val fit = TransitTimingPhysics.fit(training.timings)
        val checks = heldOut.timings.sortedBy { it.epochDays }.map {
            TransitTimingPhysics.compareHeldOut(fit, it.epochDays, it.errorDays)
        }
        val distinctCycles = checks.zipWithNext().all { (a,b) -> a.cycle < b.cycle }
        return Result(training.sector, heldOut.sector, fit, checks,
            distinctCycles && checks.all { it.compatible && it.cycleUnambiguous })
    }
}
