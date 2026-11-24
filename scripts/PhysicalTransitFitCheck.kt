import com.synthesis.service.*
import kotlin.math.*
fun main() {
    val points = (-30..30).map { i ->
        val t=i*0.005
        val z=abs(8*sin(2*PI*t/2))
        PhysicalTransitFit.Point(t,1.02*PhysicalTransitProfile.flux(z,0.1,0.3,0.2),0.0002)
    }
    fun fit(p: List<PhysicalTransitFit.Point>) = PhysicalTransitFit.fit(p,2.0,0.0,8.0,0.0,0.3,0.2)
    val r=fit(points)
    check(abs(r.radiusRatio-0.1)<0.001); println("PASS radius")
    check(abs(r.baseline-1.02)<0.0001); println("PASS baseline")
    check(r.chiSquare<r.nullChiSquare); println("PASS improvement")
    check(r.lower68!! < 0.1 && r.upper68!! > 0.1); println("PASS interval")
    val noisy=fit(points.map { it.copy(sigma=it.sigma*3) })
    check(noisy.upper68!!-noisy.lower68!! > r.upper68!!-r.lower68!!); println("PASS uncertainty scaling")
    val flat=fit(points.map { it.copy(flux=1.0) })
    check(flat.lower68 == null && flat.upper68 != null); println("PASS censored interval")
    check(!r.atBoundary); println("PASS interior")
    check(fit(points.map { it.copy(flux=1.0) }).atBoundary); println("PASS flat")
    check(runCatching { fit(points.map { it.copy(sigma=0.0) }) }.isFailure); println("PASS invalid sigma")
    check(runCatching { fit(points.map { it.copy(time=0.0) }) }.isFailure); println("PASS missing baseline")
}
