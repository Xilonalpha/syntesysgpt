package com.synthesis.service
import com.synthesis.domain.model.exoplanet.PhotometricDataPoint
fun main() {
 val p=(0..7).map { PhotometricDataPoint(0.0,2459000.0+it,1.0,0.001) }
 check(mergeMastSectorPoints(p.reversed()).map{it.minuteOffset} == (0..7).map{it*1440.0})
 check(runCatching{mergeMastSectorPoints(p+p.first())}.isFailure)
 check(runCatching{mergeMastSectorPoints(p.dropLast(1)+p.last().copy(fluxError=Double.NaN))}.isFailure)
 check(runCatching{mergeMastSectorPoints(p.take(7))}.isFailure)
 println("4 checks passed")
}
