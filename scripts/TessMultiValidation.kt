import com.synthesis.service.PeriodicTransitAnalysisEngine
import com.synthesis.engine.exoplanet.BayesianUncertaintyEngine
import java.io.File
import kotlin.math.abs

fun main(args: Array<String>) {
    require(args.size == 4) { "CSV MIN_PERIOD MAX_PERIOD EXPECTED_COMMA_LIST" }
    val lo = args[1].toDouble()
    val hi = args[2].toDouble()
    val expected = args[3].split(',').map(String::toDouble)
    require(expected.size in 1..5 && expected.distinct().size == expected.size &&
        expected.all { it.isFinite() && it in lo..hi })
    val lines = File(args[0]).readLines()
    require(lines.first() == "bjd,flux,error")
    val rows = lines.drop(1).map { line -> line.split(',').map(String::toDouble).also {
        require(it.size == 3 && it.all(Double::isFinite) && it[2] > 0)
    } }
    require(rows.size >= 8 && rows.zipWithNext().all { (a,b) -> b[0] > a[0] })
    val results = PeriodicTransitAnalysisEngine(BayesianUncertaintyEngine()).searchMultipleSignals(
        rows.map { it[0] }, rows.map { it[1] }, rows.map { it[2] }, lo, hi, 5)
    println("points=${rows.size} candidates=${results.size} search_range=$lo..$hi")
    results.forEachIndexed { i,r ->
        println("candidate=$i period=${r.bestPeriodDays} epoch=${r.epochBjd} depth_ppm=${r.transitDepthPpm} duration_hours=${r.transitDurationHours} sde=${r.signalDetectionEfficiencySde}")
    }
    val remaining = results.toMutableList()
    var matched = 0
    for (p in expected) {
        val candidate = remaining.minByOrNull { abs(it.bestPeriodDays / p - 1) }
        val hit = candidate != null && abs(candidate.bestPeriodDays / p - 1) < 0.02
        if (hit) { remaining.remove(candidate); matched++ }
        println("reference=$p matched=$hit")
    }
    println("matched=$matched expected=${expected.size} unmatched_candidates=${remaining.size}")
    println("planet_confirmation=NOT_ASSESSED global_false_alarm_probability=NOT_CALIBRATED")
    check(matched == expected.size) { "Reference recovery incomplete" }
}
