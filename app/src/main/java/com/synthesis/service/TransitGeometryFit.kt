package com.synthesis.service

/** Bounded grid search at fixed period, scaled semimajor axis and limb darkening. */
object TransitGeometryFit {
    data class Result(val epoch: Double, val impact: Double, val fit: PhysicalTransitFit.Result, val evaluations: Int)
    fun fit(points: List<PhysicalTransitFit.Point>, period: Double, epoch: Double, halfWidth: Double, a: Double, maxImpact: Double, u1: Double, u2: Double, maxRadius: Double = 0.3): Result {
        require(points.size in 8..2000 && period.isFinite() && period > 0 && epoch.isFinite())
        require(halfWidth.isFinite() && halfWidth > 0 && halfWidth < period/4 && epoch+halfWidth > epoch && epoch-halfWidth < epoch)
        require(a.isFinite() && a > 1 && maxImpact.isFinite() && maxImpact > 0 && maxImpact < a && maxImpact < 1+maxRadius)
        var best: Result? = null
        var count = 0
        var dt = halfWidth/2; var db = maxImpact/4
        fun evaluate(t: Double,b: Double) {
            count++
            val f = try { PhysicalTransitFit.fit(points,period,t,a,b,u1,u2,maxRadius,false) } catch (_: IllegalArgumentException) { return }
            if (best == null || f.chiSquare < best!!.fit.chiSquare) best = Result(t,b,f,count)
        }
        for(i in -2..2) for(j in 0..4) evaluate(epoch+i*dt,j*db)
        repeat(3) {
            val c = requireNotNull(best) { "No admissible geometry" }
            dt /= 2; db /= 2
            for(i in -2..2) for(j in -2..2) evaluate((c.epoch+i*dt).coerceIn(epoch-halfWidth,epoch+halfWidth),(c.impact+j*db).coerceIn(0.0,maxImpact))
        }
        return requireNotNull(best).copy(evaluations=count)
    }
}
