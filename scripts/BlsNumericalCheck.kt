import com.synthesis.service.PeriodicTransitAnalysisEngine
import com.synthesis.engine.exoplanet.BayesianUncertaintyEngine
import kotlin.math.*
import kotlin.random.Random
fun main() {
 val engine = PeriodicTransitAnalysisEngine(BayesianUncertaintyEngine())
 val t = List(1800) { 2460000.0 + it * 0.02 }
 fun run(period: Double, depth: Double, seed: Int): com.synthesis.domain.model.exoplanet.PeriodicTransitDetectionResult {
  val rng = Random(seed)
  val y = t.map { x -> val phase = ((x-t.first())/period + 0.5) % 1.0 - 0.5; 1.0 - (if(abs(phase)<0.04) depth else 0.0) + (rng.nextDouble()-0.5)*0.001 }
  return engine.runBlsPipeline(t,y,List(t.size){0.0003},1.0,4.0,periodSteps=1200)
 }
 for(p in listOf(1.5,2.4,3.2)) { val r=run(p,0.008,42); println("injected=$p detected=${r.isDetected} recovered=${r.bestPeriodDays} depth=${r.transitDepthPpm} duration=${r.transitDurationHours} SDE=${r.signalDetectionEfficiencySde}"); check(r.isDetected); check(abs(r.bestPeriodDays/p-1)<0.01); check(r.transitDepthPpm in 6000.0..10000.0); check(abs(r.transitDurationHours/(p*0.08*24)-1)<0.35) }
 for(seed in 1..5) { val r=run(2.4,0.0,seed); println("noise seed=$seed detected=${r.isDetected}"); check(!r.isDetected) }
 check(!engine.runBlsPipeline(t,List(t.size){1.0},List(t.size){0.0003}).isDetected)
 val rng = Random(71)
 val gapTimes = t.filterIndexed { i, _ -> i % 300 !in 100..170 }
 var noise = 0.0
 val gapFlux = gapTimes.map { x -> noise = 0.65*noise + (rng.nextDouble()-0.5)*0.0004; val phase = ((x-t.first())/2.4+0.5)%1.0-0.5; 1.0 - (if(abs(phase)<0.04) 0.003 else 0.0) + noise }
 val g = engine.runBlsPipeline(gapTimes,gapFlux,List(gapTimes.size){0.0003},1.0,4.0)
 println("gap correlated default grid: detected=${g.isDetected} period=${g.bestPeriodDays} depth=${g.transitDepthPpm} duration=${g.transitDurationHours}")
 check(g.isDetected && abs(g.bestPeriodDays/2.4-1)<0.01)
 for(depth in listOf(0.0005,0.001,0.002)) {
  val r=run(2.4,depth,42)
  println("weak injection ppm=${depth*1e6} detected=${r.isDetected} period=${r.bestPeriodDays} depth=${r.transitDepthPpm} SDE=${r.signalDetectionEfficiencySde}")
  check(r.isDetected && abs(r.bestPeriodDays/2.4-1)<0.01)
 }
 for(p in listOf(1.5,2.4,3.2)) {
  val y=t.map { 1.0+0.002*sin(2*PI*(it-t.first())/p) }
  val r=engine.runBlsPipeline(t,y,List(t.size){0.0003},1.0,4.0)
  println("stellar sinusoid period=$p detected=${r.isDetected} recovered=${r.bestPeriodDays} SDE=${r.signalDetectionEfficiencySde}")
  check(!r.isDetected)
 }
 println("16 regression datasets passed")
 // Exploratory challenges: report outcomes, not a claim of validated classification.
 for(amplitude in listOf(0.0002,0.0005,0.001,0.002)) {
  val y=t.map { x -> val dt=x-t.first(); val phase=(dt/2.4+0.5)%1.0-0.5; 1.0-(if(abs(phase)<0.04) 0.0005 else 0.0)+amplitude*sin(2*PI*dt/3.2) }
  val r=engine.runBlsPipeline(t,y,List(t.size){0.0003},1.0,4.0)
  println("mixed amplitude=$amplitude detected=${r.isDetected} period=${r.bestPeriodDays} correct=${r.isDetected && abs(r.bestPeriodDays/2.4-1)<0.01}")
 }
 for(ratio in listOf(0.5,1.0)) {
  val y=t.map { x -> val phase=((x-t.first())/3.2)%1.0; 1.0-(if(phase<0.04 || phase>0.96) 0.004 else if(abs(phase-0.5)<0.04) 0.004*ratio else 0.0) }
  val r=engine.runBlsPipeline(t,y,List(t.size){0.0003},1.0,4.0)
  println("binary secondary ratio=$ratio detected=${r.isDetected} period=${r.bestPeriodDays}")
 }
}
