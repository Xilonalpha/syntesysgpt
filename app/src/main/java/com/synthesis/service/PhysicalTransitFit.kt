package com.synthesis.service

import kotlin.math.*

/** Conditional radius fit at fixed circular-orbit geometry and limb darkening. */
object PhysicalTransitFit {
    data class Point(val time: Double, val flux: Double, val sigma: Double, val exposure: Double = 0.0)
    data class Result(val radiusRatio: Double, val baseline: Double, val chiSquare: Double, val nullChiSquare: Double, val atBoundary: Boolean, val lower68: Double? = null, val upper68: Double? = null)
    fun fit(points: List<Point>, period: Double, epoch: Double, a: Double, impact: Double,
            u1: Double, u2: Double, maxRadius: Double = 0.3, computeInterval: Boolean = true): Result {
        require(points.size in 8..10000 && period.isFinite() && period > 0 && epoch.isFinite())
        require(a.isFinite() && a > 1 && impact.isFinite() && impact >= 0 && impact < a)
        require(maxRadius.isFinite() && maxRadius in 0.001..1.0)
        require(points.all { it.time.isFinite() && it.flux.isFinite() && it.sigma.isFinite() && it.sigma > 0 && it.exposure.isFinite() && it.exposure in 0.0..period })
        val minSigma = points.minOf { it.sigma }
        val weights = points.map { (minSigma/it.sigma).pow(2) }
        fun model(p: Point, k: Double): Double {
            val n = if (p.exposure == 0.0) 1 else 9
            return (0 until n).sumOf { j ->
                val phase = 2*PI*((p.time-epoch)/period + ((j+0.5)/n-0.5)*p.exposure/period)
                if (cos(phase) <= 0) 1.0 else PhysicalTransitProfile.flux(hypot(a*sin(phase),impact*cos(phase)),k,u1,u2,128)
            }/n
        }
        fun evaluate(k: Double): Result {
            val m = points.map { model(it,k) }
            val baseline = points.indices.sumOf { weights[it]*m[it]*points[it].flux } / points.indices.sumOf { weights[it]*m[it]*m[it] }
            require(baseline.isFinite() && baseline > 0)
            val chi = points.indices.sumOf { ((points[it].flux-baseline*m[it])/points[it].sigma).pow(2) }
            require(chi.isFinite())
            return Result(k,baseline,chi,0.0,false)
        }
        require(points.any { model(it,maxRadius) < 0.999999 } && points.any { model(it,maxRadius) == 1.0 })
        val nullFit = evaluate(0.0)
        var best = nullFit
        var step = maxRadius/24
        for (i in 1..24) { val r = evaluate(i*step); if (r.chiSquare < best.chiSquare) best = r }
        repeat(4) {
            val center = best.radiusRatio
            step /= 4
            for (i in -4..4) { val r = evaluate((center+i*step).coerceIn(0.0,maxRadius)); if (r.chiSquare < best.chiSquare) best = r }
        }
        // Find the first sampled crossing on each side of the optimum.
        // Null indicates that the interval is censored by the search domain.
        fun limit(edge: Double): Double? {
            val threshold = best.chiSquare + 1.0
            var inside = best.radiusRatio
            for (i in 1..64) {
                val outside = best.radiusRatio + (edge-best.radiusRatio)*i/64
                if (evaluate(outside).chiSquare >= threshold) {
                    var lo = inside
                    var hi = outside
                    repeat(20) {
                        val mid = (lo+hi)/2
                        if (evaluate(mid).chiSquare < threshold) lo = mid else hi = mid
                    }
                    return (lo+hi)/2
                }
                inside = outside
            }
            return null
        }
        return best.copy(lower68=if (computeInterval) limit(0.0) else null, upper68=if (computeInterval) limit(maxRadius) else null, nullChiSquare=nullFit.chiSquare, atBoundary=best.radiusRatio <= step || best.radiusRatio >= maxRadius-step)
    }
}
