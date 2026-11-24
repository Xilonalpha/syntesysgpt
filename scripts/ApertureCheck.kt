import com.synthesis.service.*
import kotlin.math.*
fun main() {
 val engine = TelescopeVisionPhotometryEngine(PeriodicTransitAnalysisEngine(com.synthesis.engine.exoplanet.BayesianUncertaintyEngine()))
 val w=101
 val pixels=FloatArray(w*w) { i -> val x=i%w-50; val y=i/w-50; (100+1000*exp(-(x*x+y*y)/8.0)).toFloat() }
 fun star(gain:Double?=null,sat:Double?=null)=engine.computeStarDetails(1,50,50,pixels,w,w,100f,1f,gainElectronsPerAdu=gain,saturationAdu=sat)!!
 val a=star(2.0); check(abs(a.x-50)<0.01 && abs(a.y-50)<0.01)
 val expected=pixels.indices.filter { val x=it%w-50; val y=it/w-50; x*x+y*y<=36 }.sumOf { pixels[it].toDouble()-100 }
 check(abs(a.netFlux-expected)<0.01)
 check(abs(a.snr-sqrt(2*a.netFlux))<0.01)
 check(star(2.0,1000.0).isSaturated)
 check(!star(2.0,2000.0).isSaturated)
 check(engine.computeStarDetails(1,5,5,pixels,w,w,100f,1f)==null)
 check(engine.processFramePhotometry(1,1,0.0,2459000.0,a,emptyList(),1.0,50f,50f)==null)
 val detected=engine.detectStars(pixels,w,w,gainElectronsPerAdu=2.0,saturationAdu=1000.0).single()
 check(detected.isSaturated)
 val tracked=engine.trackStarCentroid(a,pixels,w,w,gainElectronsPerAdu=2.0,saturationAdu=1000.0)!!
 check(tracked.isSaturated)
 check(abs(tracked.snr-a.snr)<0.01)
 pixels[50*w+50]=Float.NaN
 check(engine.computeStarDetails(1,50,50,pixels,w,w,100f,1f)==null)
 println("11 aperture checks passed")
}
