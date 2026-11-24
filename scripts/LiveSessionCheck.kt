import com.synthesis.domain.model.exoplanet.LiveObservationSession
fun main() {
 val s = LiveObservationSession()
 check(s.dataPoints.isEmpty() && !s.isRealImportedData)
 check(s.calculatedMidTransitBjd.isNaN() && s.chiSquaredPerDof.isNaN())
 check(s.expectedDepthPpm.isNaN() && s.expectedDurationMinutes.isNaN())
 check(s.candidateName == "Necunoscut" && s.hostStar == "Necunoscut")
 println("4 session checks passed")
}
