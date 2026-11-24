import com.synthesis.service.*
import com.synthesis.engine.exoplanet.BayesianUncertaintyEngine
import kotlin.math.*
fun main() {
 for (noise in listOf(0.0, 0.0002)) {
 val engine=PeriodicTransitAnalysisEngine(BayesianUncertaintyEngine())
 val epoch=2460000.1; val period=2.4
 val t=List(600){2460000.0+it*0.04}
 val y=t.map { val ph=2*PI*(it-epoch)/period; if(cos(ph)>0) PhysicalTransitProfile.flux(hypot(6*sin(ph),0.4*cos(ph)),0.1,0.3,0.2,128) else 1.0 }.mapIndexed { i, f -> f + noise*sin(i*1.713) }
 val sigma=List(t.size){0.0003}
 val r=engine.runBlsPipeline(t,y,sigma,1.5,3.5)
 println("BLS detected=${r.isDetected} period=${r.bestPeriodDays} status=${r.statusSummary}")
 check(r.isDetected && abs(r.bestPeriodDays/period-1)<0.01)
 val fit=BlsPhysicalRefinement.refine(r,t.indices.map { PhysicalTransitFit.Point(t[it],y[it],sigma[it]) },6.0,0.8,0.3,0.2)
 println("radius=${fit.geometry.fit.radiusRatio} physicalBIC=${fit.physicalBic} flatBIC=${fit.flatBic}")
 check(abs(fit.geometry.fit.radiusRatio-0.1)<0.02 && fit.physicalBic<fit.flatBic)
 val flat=engine.runBlsPipeline(t,List(t.size){1.0},sigma,1.5,3.5)
 check(!flat.isDetected && runCatching { BlsPhysicalRefinement.refine(flat,t.indices.map { PhysicalTransitFit.Point(t[it],1.0,sigma[it]) },6.0,0.8,0.3,0.2) }.isFailure)
 println("PASS integrated transit and flat rejection noise=$noise")
 }
}
