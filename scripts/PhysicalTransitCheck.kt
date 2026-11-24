import com.synthesis.service.PhysicalTransitProfile as P
import kotlin.math.*
fun main() {
    var n=0
    fun test(name:String, ok:Boolean) { check(ok){name}; println("PASS $name"); n++ }
    test("outside", P.flux(1.2,.1,.3,.2)==1.0)
    test("zero radius", P.flux(0.0,0.0,.3,.2)==1.0)
    test("uniform central", abs(P.flux(0.0,.1,0.0,0.0)-.99)<1e-12)
    test("uniform interior", abs(P.flux(.5,.1,0.0,0.0)-.99)<1e-6)
    test("total occultation", P.flux(0.0,1.0,0.0,0.0)<1e-12)
    test("limb darkening", P.flux(0.0,.1,.3,.2)<P.flux(.8,.1,.3,.2))
    test("partial contact", P.flux(1.0,.1,0.0,0.0) in .99..1.0)
    test("convergence", abs(P.flux(.95,.1,.3,.2,512)-P.flux(.95,.1,.3,.2,4096))<1e-6)
    test("invalid input", runCatching{P.flux(Double.NaN,.1,.3,.2)}.isFailure)
    test("invalid limb coefficients", runCatching{P.flux(0.0,.1,.9,.9)}.isFailure)
    println("$n/10 checks passed")
}
