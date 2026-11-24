import com.synthesis.domain.model.*
import com.synthesis.engine.discovery.*
import com.synthesis.engine.normalizer.DataNormalizer
import com.synthesis.engine.hypothesis.ImpactIpEngine
import com.synthesis.data.repository.SynthesisRepository
import kotlinx.coroutines.runBlocking
fun main() = runBlocking {
    val observations = (1..12).map { Observation("o$it", "i", "s$it", "source", "target", it.toLong(), null, Measurement(it.toDouble(), "flux"), "", "", 0.1f, ObservationType.ASTRONOMICAL) }
    val pattern = PatternDetector.DetectedPattern(PatternType.CAUSAL_CHAIN,"test",1f,listOf("o1"),listOf("s1"),listOf("ASTRONOMICAL"))
    val d = Discovery(investigationId="i",title="test",description="test",discoveryType=DiscoveryType.CAUSAL_LINK,noveltyScore=1f,confidenceScore=1f,impactScore=1f,commercialScore=1f,patentabilityScore=1f,estimatedValueUsd=1000000,supportingEvidence=emptyList(),domains=emptyList(),sourceIds=emptyList(),hiddenPattern=HiddenPattern(PatternType.CAUSAL_CHAIN,"test",1f,1f,0,1),recommendedActions=emptyList(),risks=emptyList())
    val p = PatentabilityEngine(); val c = CommercialValueEstimator(); val detector = PatternDetector(DataNormalizer())
    check(detector.detectHiddenPatterns(observations).isEmpty())
    check(DiscoveryEngine(detector,p,c).generateDiscoveries("i",observations).isEmpty())
    check(p.assess(d,pattern)==null)
    check(c.estimate(d,PatentAssessment(d.id,1f,1f,1f,0f,null,"",1f,1000000,12))==null)
    check(ImpactIpEngine(SynthesisRepository()).assessDiscovery("i",observations,emptyList(),emptyList(),emptyList())==null)
    println("5 checks passed")
}
