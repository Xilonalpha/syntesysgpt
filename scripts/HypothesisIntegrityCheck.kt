import com.synthesis.domain.model.*
import com.synthesis.engine.hypothesis.HypothesisEngine
fun main() {
    val engine = HypothesisEngine()
    val obs = Observation("o", "i", "s", "source", "target", 0, null, Measurement(1.0,"flux"), "", "", 0.1f, ObservationType.ASTRONOMICAL)
    check(engine.generateHypotheses(emptyList(), emptyList(), emptyList()).isEmpty())
    val h = engine.generateHypotheses(listOf(obs), emptyList(), emptyList())
    check(h.size == 4 && h.all { it.status == HypothesisStatus.PENDING && it.confidence == 0f && it.evidenceCount == 0 && it.contradictionCount == 0 })
    check(h.map { it.id }.toSet().size == 4)
    check(runCatching { engine.generateHypotheses(listOf(obs, obs.copy(investigationId="other")), emptyList(), emptyList()) }.isFailure)
    check(engine.generateNextActions(h).size == 4)
    println("5 checks passed")
}
