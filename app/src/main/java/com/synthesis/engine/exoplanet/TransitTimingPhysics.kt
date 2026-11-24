package com.synthesis.engine.exoplanet

import kotlin.math.sqrt

data class TransitTiming(val cycle: Int, val epochDays: Double, val errorDays: Double)
data class TimingResidual(val cycle: Int, val residualMinutes: Double)
data class LinearEphemeris(val referenceCycle: Int, val epochDays: Double, val periodDays: Double,
    val periodErrorDays: Double, val chiSquared: Double, val degreesOfFreedom: Int,
    val residuals: List<TimingResidual>, val epochErrorDays: Double = Double.NaN,
    val epochPeriodCovariance: Double = Double.NaN)

/** Independent timing errors, known integer cycles and common time scale.
 * Fits a constant period; excess residuals do not establish a companion.
 */
object TransitTimingPhysics {
    data class SectorEpoch(val sector: Int, val epochBjd: Double, val errorDays: Double, val productSha256: String)
    data class PhaseComparison(val cycle: Long, val residualDays: Double, val uncertaintyDays: Double,
        val compatible: Boolean, val cycleUnambiguous: Boolean)

    /** Conditional constant-period comparison; caller establishes same target, BJD_TDB
     * time scale and independent acquisition. No planet confirmation is implied. */
    fun compareSectors(first: SectorEpoch, second: SectorEpoch, periodDays: Double,
        periodErrorDays: Double): PhaseComparison {
        require(first.sector > 0 && second.sector > 0 && first.sector != second.sector)
        require(listOf(first, second).all { it.epochBjd.isFinite() && it.epochBjd > 2400000 &&
            it.errorDays.isFinite() && it.errorDays > 0 && it.productSha256.matches(Regex("[0-9a-fA-F]{64}")) })
        require(!first.productSha256.equals(second.productSha256, ignoreCase = true))
        require(periodDays.isFinite() && periodDays > 0 && periodErrorDays.isFinite() && periodErrorDays > 0)
        val delta = second.epochBjd - first.epochBjd
        val cycles = delta / periodDays
        require(cycles.isFinite() && kotlin.math.abs(cycles) < 1e9 && kotlin.math.abs(cycles) >= 0.5)
        val cycle = kotlin.math.round(cycles).toLong()
        val residual = delta - cycle * periodDays
        val uncertainty = kotlin.math.hypot(kotlin.math.hypot(first.errorDays, second.errorDays),
            cycle.toDouble() * periodErrorDays)
        require(uncertainty.isFinite() && uncertainty > 0)
        val unambiguous = kotlin.math.abs(residual) + 3 * uncertainty < periodDays / 2
        return PhaseComparison(cycle, residual, uncertainty,
            unambiguous && kotlin.math.abs(residual) <= 3 * uncertainty, unambiguous)
    }

    /** Predict an independent held-out transit from a fitted ephemeris, including covariance.
     * Caller must establish independent acquisition and a common time scale. */
    fun compareHeldOut(ephemeris: LinearEphemeris, epochBjd: Double, errorDays: Double): PhaseComparison {
        require(epochBjd.isFinite() && epochBjd > 2400000 && errorDays.isFinite() && errorDays > 0)
        require(ephemeris.epochDays.isFinite() && ephemeris.epochDays > 2400000)
        val p = ephemeris.periodDays
        val se = ephemeris.epochErrorDays
        val sp = ephemeris.periodErrorDays
        val cov = ephemeris.epochPeriodCovariance
        require(p.isFinite() && p > 0 && se.isFinite() && se > 0 && sp.isFinite() && sp > 0)
        require(cov.isFinite() && kotlin.math.abs(cov) <= se * sp)
        val cycles = (epochBjd - ephemeris.epochDays) / p
        require(cycles.isFinite() && kotlin.math.abs(cycles) < 1e9)
        val n = kotlin.math.round(cycles).toLong()
        val x = n.toDouble()
        val variance = se * se + x * x * sp * sp + 2 * x * cov
        require(variance.isFinite() && variance >= 0)
        val uncertainty = kotlin.math.hypot(sqrt(variance), errorDays)
        val residual = (epochBjd - ephemeris.epochDays) - x * p
        val unique = kotlin.math.abs(residual) + 3 * uncertainty < p / 2
        return PhaseComparison(n, residual, uncertainty, unique && kotlin.math.abs(residual) <= 3 * uncertainty, unique)
    }

    fun fit(observations: List<TransitTiming>): LinearEphemeris {
        require(observations.size >= 3)
        val p = observations.sortedBy { it.cycle }
        require(p.all { it.epochDays.isFinite() && it.errorDays.isFinite() && it.errorDays > 0 })
        require(p.zipWithNext().all { (a,b) -> a.cycle < b.cycle && a.epochDays < b.epochDays })
        val ref = p.first()
        val x = p.map { it.cycle.toDouble() - ref.cycle.toDouble() }
        val y = p.map { it.epochDays - ref.epochDays }
        val w = p.map { 1.0 / (it.errorDays * it.errorDays) }
        require(w.all { it.isFinite() && it > 0 })
        val sw = w.sum()
        require(sw.isFinite() && sw > 0)
        val mx = x.indices.sumOf { w[it] * x[it] } / sw
        val my = y.indices.sumOf { w[it] * y[it] } / sw
        val sxx = x.indices.sumOf { w[it] * (x[it]-mx)*(x[it]-mx) }
        require(sxx.isFinite() && sxx > 0)
        val period = x.indices.sumOf { w[it]*(x[it]-mx)*(y[it]-my) } / sxx
        val intercept = my-period*mx
        val residual = x.indices.map { y[it]-intercept-period*x[it] }
        val chi = residual.indices.sumOf { val z = residual[it]/p[it].errorDays; z*z }
        require(period.isFinite() && period > 0 && intercept.isFinite() && chi.isFinite())
        val epoch = ref.epochDays + intercept
        require(epoch.isFinite() && residual.all { (it*1440).isFinite() })
        return LinearEphemeris(ref.cycle, epoch, period, sqrt(1/sxx), chi, p.size-2,
            p.indices.map { TimingResidual(p[it].cycle, residual[it]*1440) },
            sqrt(1/sw + mx*mx/sxx), -mx/sxx)
    }
}
