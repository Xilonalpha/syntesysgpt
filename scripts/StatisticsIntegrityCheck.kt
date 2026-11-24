import com.synthesis.domain.model.*
import com.synthesis.engine.normalizer.DataNormalizer
import com.synthesis.engine.anomaly.AnomalyDetector
import com.synthesis.engine.correlation.CorrelationEngine
fun main() {
 val n=DataNormalizer()
 fun obs(i:Int, source:String="a", value:Double=i.toDouble(), unit:String="m", entity:String="target")=Observation("$source$i","inv",source,source,entity,i*86400000L,null,Measurement(value,unit),"","",9f,ObservationType.ASTRONOMICAL)
 check(kotlin.math.abs(n.normalize(obs(0,value=32.0,unit="°F")).uncertainty-5f)<1e-5)
 check(n.normalize(obs(0,unit="mW")).unit=="mW")
 val c=CorrelationEngine(n)
 val a=(0..9).map{obs(it)}; val b=(0..9).map{obs(it,"b",-it.toDouble())}
 val r=c.correlate(a+b,"inv").single(); check(r.strength>0.999f && r.description.contains("n=10") && !r.causalityEstablished && r.description.contains("significance not assessed"))
 check(c.correlate(a+b.take(9),"inv").isEmpty())
 check(c.correlate(a+b,"other").isEmpty())
 val d=AnomalyDetector(n)
 check(d.detectAnomalies((0..19).map{obs(it,value=if(it==19) 100.0 else 0.0)}).single().possibleCauses.isEmpty())
 check(d.detectAnomalies((0..19).map{obs(it,value=if(it==19) 100.0 else 0.0,entity=if(it==19) "other" else "target")}).isEmpty())
 println("7 checks passed")
}
