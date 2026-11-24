package com.synthesis.service
import com.synthesis.domain.model.exoplanet.PhotometricDataPoint
import java.nio.file.Files
fun main() {
 val root=Files.createTempDirectory("replay").toFile()
 try {
 val e=FitsEvidenceStore(root.resolve("fits")); val m=ExperimentManifestStore(root.resolve("manifests"),e)
 val hash=e.save(byteArrayOf(1,2,3)); val source=ExperimentManifestStore.Source("mast:TESS/product/test_lc.fits",hash,1,8)
 val id=m.save(42,listOf(source),ExperimentReplay.ALGORITHM); val r=ExperimentReplay(m,e)
 val p=(0..7).map { PhotometricDataPoint(0.0,2459000.0+it,1.0,0.001) }
 val s=ExperimentReplay.Sector(42,"TESS",1,p.reversed())
 check(r.load(id){s}.map{it.minuteOffset}==(0..7).map{it*1440.0})
 check(runCatching{r.load(id){s.copy(tic=43)}}.isFailure)
 check(runCatching{r.load(id){s.copy(sector=2)}}.isFailure)
 check(runCatching{r.load(id){s.copy(points=p.take(7))}}.isFailure)
 check(runCatching{r.load(id){s.copy(mission="Kepler")}}.isFailure)
 val old=m.save(42,listOf(source),"BLS-patch09")
 check(runCatching{r.load(old){s}}.isFailure)
 println("6 checks passed")
 } finally {root.deleteRecursively()}
}
