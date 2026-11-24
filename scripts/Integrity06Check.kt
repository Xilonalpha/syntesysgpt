import com.synthesis.engine.exoplanet.BayesianUncertaintyEngine
import com.synthesis.engine.flight.MissionFlightBenchmarkEngine
fun main() {
 val engine = BayesianUncertaintyEngine()
 fun calc(error: Double = 35.0, chi: Double = 10.0, dof: Int = 100) = engine.propagatePhysicalUncertainties(624.0,error,37.426,0.005,0.420,0.015,0.416,0.020,3480.0,90.0,chi,dof)
 check(kotlin.math.abs(calc().reducedChiSquared - 0.1) < 1e-12)
 check(runCatching { calc(Double.NaN) }.isFailure)
 check(runCatching { calc(dof=0) }.isFailure)
 check(runCatching { calc(chi=-1.0) }.isFailure)
 val d = MissionFlightBenchmarkEngine(engine).runFullFlightQualificationSuite()
 check(!d.isMissionCertified && d.assessedTrlLevel == 0)
 check(d.benchmarkResults.all { it.chiSquaredNu == null && it.bayesianConfidenceScore == null })
 check(!d.protocolCompliance.nasaTfopSg1Compliant && !d.protocolCompliance.ivoaVoEventCompliant && !d.protocolCompliance.ivoaVoTableCompliant && !d.protocolCompliance.aavsoEtdCompliant && !d.protocolCompliance.nasaExoFopCtoiCompliant)
 println("7 checks passed")
}
