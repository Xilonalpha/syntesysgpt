package com.synthesis.engine.exoplanet

import kotlin.math.sqrt

data class TransitTiming(val cycle: Int, val epochDays: Double, val errorDays: Double)
data class TimingResidual(val cycle: Int, val residualMinutes: Double)
data class LinearEphemeris(val referenceCycle: Int, val epochDays: Double, val periodDays: Double,
    val periodErrorDays: Double, val chiSquared: Double, val degreesOfFreedom: Int,
    val residuals: List<TimingResidual>)

/** Independent timing errors, known integer cycles and common time scale.
 * Fits a constant period; excess residuals do not establish a companion.
 */
object TransitTimingPhysics {
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
            p.indices.map { TimingResidual(p[it].cycle, residual[it]*1440) })
    }
}
