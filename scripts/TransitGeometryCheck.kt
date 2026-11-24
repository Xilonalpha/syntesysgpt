import com.synthesis.service.*
import kotlin.math.*
fun main() {
    val points=(-20..20).map { i -> val t=i*0.006; val p=PI*(t-0.012); PhysicalTransitFit.Point(t,PhysicalTransitProfile.flux(hypot(8*sin(p),0.4*cos(p)),0.1,0.3,0.2,128),0.0002) }
    val r=TransitGeometryFit.fit(points,2.0,0.0,0.024,8.0,0.8,0.3,0.2)
    check(abs(r.epoch-0.012)<0.002); println("PASS epoch")
    check(abs(r.impact-0.4)<0.06); println("PASS impact")
    check(abs(r.fit.radiusRatio-0.1)<0.005); println("PASS radius")
    check(r.evaluations==100); println("PASS bounded evaluations")
    check(r.fit.lower68==null && r.fit.upper68==null); println("PASS no conditional interval")
    check(runCatching { TransitGeometryFit.fit(points,2.0,0.0,0.0,8.0,0.8,0.3,0.2) }.isFailure); println("PASS invalid bounds")
}
