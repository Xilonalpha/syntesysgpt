import com.synthesis.service.PeriodicTransitAnalysisEngine
import com.synthesis.engine.exoplanet.BayesianUncertaintyEngine
import kotlin.math.*
import java.util.Random

fun main(args: Array<String>) {
    val trials = args.firstOrNull()?.toInt() ?: 20
    require(trials in 1..1000)
    val seed = args.getOrNull(1)?.toLong() ?: 111L
    val nullOnly = args.getOrNull(2) == "null"
    val rho = args.getOrNull(3)?.toDouble() ?: 0.8
    require(rho.isFinite() && rho in 0.0..0.99)
    val engine = PeriodicTransitAnalysisEngine(BayesianUncertaintyEngine())
    val rng = Random(seed)
    println("kind,trial,depth_ppm,injected_period,recovered_period,accepted,recovered")
    for (depth in if (nullOnly) listOf(0.0) else listOf(0.0, 300.0, 600.0, 1500.0, 5000.0)) {
        var accepted = 0
        var recovered = 0
        repeat(trials) { trial ->
            val period = 0.8 + rng.nextDouble() * 5.2
            val epoch = rng.nextDouble() * period
            val times = (0 until 1400).filter { it !in 600..680 }.map { it * 0.02 }
            var red = 0.0
            val flux = times.map { t ->
                red = rho * red + 0.0003 * rng.nextGaussian()
                val phase = (t - epoch) / period
                val dip = if (abs(phase - round(phase)) < 0.025) depth / 1e6 else 0.0
                1.0 - dip + red + 0.0005 * rng.nextGaussian()
            }
            val r = engine.runBlsPipeline(times.map { 2460000.0 + it }, flux,
                List(times.size) { 0.0005 }, 0.5, 10.0)
            val hit = depth > 0 && r.isDetected && abs(r.bestPeriodDays / period - 1) < 0.02
            if (r.isDetected) accepted++
            if (hit) recovered++
            println("${if(depth == 0.0) "null" else "injection"},$trial,$depth,${if(depth == 0.0) "" else period},${r.bestPeriodDays},${r.isDetected},$hit")
        }
        System.err.println("depth_ppm=$depth trials=$trials accepted=$accepted recovered=$recovered")
    }
    System.err.println("Synthetic correlated-noise experiment only; not an astrophysical false-alarm calibration.")
}
