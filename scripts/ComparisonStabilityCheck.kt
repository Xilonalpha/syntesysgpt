import com.synthesis.service.ComparisonStability
import com.synthesis.domain.model.exoplanet.DetectedStar
fun main() {
 fun star(id:Int, flux:Double)=DetectedStar(id,0f,0f,0f,2f,flux,0.0,flux,10000.0)
 val gate=ComparisonStability(); val a=listOf(star(1,100.0),star(2,200.0))
 check(!gate.accept(a.take(1)))
 check(gate.accept(a))
 check(gate.accept(listOf(star(2,100.0),star(1,50.0))))
 check(!gate.accept(listOf(star(1,110.0),star(2,200.0))))
 check(!gate.accept(listOf(star(1,100.0),star(3,200.0))))
 check(!gate.accept(a.map { it.copy(isSaturated=true) }))
 check(gate.accept(a)); gate.reset(); check(gate.accept(listOf(star(1,110.0),star(2,200.0))))
 println("8 comparison stability checks passed")
}
