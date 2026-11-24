import com.synthesis.service.PeriodicTransitAnalysisEngine
import com.synthesis.engine.exoplanet.BayesianUncertaintyEngine
import java.io.File
import kotlin.math.abs
fun main(args: Array<String>) {
 require(args.size == 2) { "Usage: CSV expected_period_days" }
 val control=args[1]=="control"
 val expected=if(control) null else args[1].toDouble().also { require(it.isFinite() && it in 0.5..10.0) }
 val lines=File(args[0]).readLines(); require(lines.first()=="bjd,flux,error")
 val rows=lines.drop(1).map { line -> line.split(',').map(String::toDouble).also { require(it.size==3 && it.all(Double::isFinite) && it[2]>0) } }
 require(rows.size>=8 && rows.zipWithNext().all { (a,b)->b[0]>a[0] })
 val r=PeriodicTransitAnalysisEngine(BayesianUncertaintyEngine()).runBlsPipeline(rows.map{it[0]},rows.map{it[1]},rows.map{it[2]},0.5,10.0)
 println("points=${rows.size} detected=${r.isDetected} period=${r.bestPeriodDays} expected=$expected status=${r.statusSummary}")
 if(control) check(!r.isDetected) { "Control produced a detection; review required" }
 else check(r.isDetected && abs(r.bestPeriodDays/requireNotNull(expected)-1)<0.02) { "Period recovery failed" }
}
