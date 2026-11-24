import com.synthesis.service.*
import kotlin.math.*
fun main() {
 val p=(-20..20).map { i -> val t=i*0.006; val ph=PI*(t-0.012); PhysicalTransitFit.Point(t,PhysicalTransitProfile.flux(hypot(8*sin(ph),0.4*cos(ph)),0.1,0.3,0.2,128),0.0002) }
 val r=BlsPhysicalRefinement.refineSeed(p,2.0,0.0,0.048,8.0,0.8,0.3,0.2)
 check(abs(r.geometry.epoch-0.012)<0.002); println("PASS epoch")
 check(r.geometry.fit.chiSquare < r.boxChiSquare && r.boxChiSquare < r.flatChiSquare); println("PASS same-sample comparison")
 check(r.physicalBic < r.boxBic && r.boxBic < r.flatBic); println("PASS conditional BIC")
 check(runCatching { BlsPhysicalRefinement.refineSeed(p,2.0,0.0,0.0,8.0,0.8,0.3,0.2) }.isFailure); println("PASS invalid duration")
 check(runCatching { BlsPhysicalRefinement.refineSeed(p.map { it.copy(exposure=-0.001) },2.0,0.0,0.048,8.0,0.8,0.3,0.2) }.isFailure); println("PASS invalid exposure")
 fun f(t:Double,e:Double)=BlsPhysicalRefinement.exposureFraction(t,0.0,2.0,0.2,e)
 check(abs(f(0.1,0.1)-0.5)<1e-12); println("PASS partial exposure")
 check(abs(f(2.0,0.1)-1)<1e-12 && f(1.0,0.1)==0.0); println("PASS periodic exposure")
 check(abs(f(0.9,2.0)-0.1)<1e-12); println("PASS full period exposure")
 val finite=p.map { it.copy(exposure=0.001) }
 val q=BlsPhysicalRefinement.refineSeed(finite,2.0,0.0,0.048,8.0,0.8,0.3,0.2)
 check(q.boxChiSquare < q.flatChiSquare && q.physicalBic.isFinite()); println("PASS finite exposure comparison")
}
