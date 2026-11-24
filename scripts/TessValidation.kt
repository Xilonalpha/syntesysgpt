import com.synthesis.service.PeriodicTransitAnalysisEngine
import com.synthesis.engine.exoplanet.BayesianUncertaintyEngine
import java.io.File
import kotlin.math.abs
fun main(args: Array<String>) {
 require(args.size == 2 || args.size == 5 || args.size == 6) { "Usage: CSV expected_period_days" }
 val lo=System.getenv("XILON_SEARCH_MIN")?.toDouble() ?: 0.5
 val hi=System.getenv("XILON_SEARCH_MAX")?.toDouble() ?: 10.0
 require(lo.isFinite() && hi.isFinite() && lo>=0.02 && hi>lo)
 val control=args[1]=="control"
 val expected=if(control) null else args[1].toDouble().also { require(it.isFinite() && it in lo..hi) }
 val stellar = if (args.size >= 5) args.drop(2).take(3).map { it.toDouble().let { v -> if (v.isFinite() && v > 0) v else Double.NaN } } else List(3) { Double.NaN }
 println("stellar_radius_solar=${stellar[0]} stellar_mass_solar=${stellar[1]} stellar_teff_k=${stellar[2]}")
 val constrained = args.size == 6 && args[5] == "constrained"
 require(args.size != 6 || constrained)
 println("duration_constrained=$constrained")
 val lines=File(args[0]).readLines(); require(lines.first()=="bjd,flux,error")
 val rows=lines.drop(1).map { line -> line.split(',').map(String::toDouble).also { require(it.size==3 && it.all(Double::isFinite) && it[2]>0) } }
 require(rows.size>=8 && rows.zipWithNext().all { (a,b)->b[0]>a[0] })
 val r=PeriodicTransitAnalysisEngine(BayesianUncertaintyEngine()).runBlsPipeline(rows.map{it[0]},rows.map{it[1]},rows.map{it[2]},lo,hi, hostStarRadiusSolar=stellar[0], hostStarMassSolar=stellar[1], hostStarTeffK=stellar[2], constrainDuration=constrained)
 println("points=${rows.size} detected=${r.isDetected} period=${r.bestPeriodDays} expected=$expected status=${r.statusSummary}")
 println("search_min_days=$lo search_max_days=$hi tolerance_fraction=0.02")
 println("depth_ppm=${r.transitDepthPpm} duration_hours=${r.transitDurationHours} snr=${r.transitSnr} epoch_bjd=${r.epochBjd}")
 val validPeriod=r.bestPeriodDays.isFinite() && r.bestPeriodDays>0
 val error=if(expected!=null && validPeriod) abs(r.bestPeriodDays/expected-1) else null
 println("relative_period_error=$error")
 println("sde=${r.signalDetectionEfficiencySde}")
 println("odd_depth_ppm=${r.vetting.oddTransitDepthPpm} even_depth_ppm=${r.vetting.evenTransitDepthPpm} odd_even_sigma=${r.vetting.oddEvenSigmaDiff} odd_even_consistent=${r.vetting.isOddEvenConsistent}")
 println("secondary_depth_ppm=${r.vetting.secondaryEclipseDepthPpm} secondary_sigma=${r.vetting.secondaryEclipseSigma} secondary_detected=${r.vetting.isSecondaryEclipseDetected}")
 println("period_agreement=${if(control) "NOT_APPLICABLE" else if(error!=null && error<0.02) "PASS" else "FAIL"}")
 println("candidate_accepted=${r.isDetected}")
 check(r.periodogram.isNotEmpty()) { "Search not completed; cannot classify benchmark" }
 val passed=if(control) !r.isDetected else r.isDetected && error!=null && error<0.02
 println("benchmark_status=${if(passed) "PASS" else "FAIL"}")
 println("planet_confirmation=NOT_ASSESSED global_false_alarm_probability=NOT_CALIBRATED")
 check(passed) { if(control) "Control produced a detection; review required" else "Positive-control benchmark failed: inspect period agreement and candidate vetting separately" }
}
