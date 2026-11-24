import com.synthesis.service.PhotometryHistory
import com.synthesis.service.FitsEvidenceStore
import com.synthesis.domain.model.exoplanet.FramePhotometrySample
import java.nio.file.Files
fun main() {
 val root=Files.createTempDirectory("phot-history").toFile()
 try {
 val e=FitsEvidenceStore(root.resolve("fits")); val hash=e.save(byteArrayOf(1,2,3)); val h=PhotometryHistory(root,evidence=e); val id=h.start()
 val s=FramePhotometrySample(1,2,0.0,2459000.0,100.0,200.0,.5,1.0,.01,1f,2f,0f,0f,2f,3.0)
 h.append(id,s,hash); h.append(id,s.copy(frameIndex=2,bjd=2459001.0),hash)
 check(PhotometryHistory(root).sessions()==listOf(id))
 check(root.resolve("$id.v2.csv").readLines().size==2)
 check(runCatching { h.append(id,s.copy(bjd=Double.NaN),hash) }.isFailure)
 check(runCatching { PhotometryHistory(root,1,e).append(id,s,hash) }.isFailure)
 check(runCatching { h.append("../bad",s,hash) }.isFailure)
 check(h.verify(id).size==2)
 check(runCatching { h.append(id,s,hash) }.isFailure)
 root.resolve("$id.v2.csv").appendText("partial")
 check(runCatching { h.verify(id) }.isFailure)
 val original = root.resolve("$id.v2.csv").readBytes()
 val recovered = h.recoverPrefix(id)
 check(recovered.incompleteTail && recovered.rows.size == 2)
 check(root.resolve("$id.v2.csv").readBytes().contentEquals(original))
 root.resolve("fits/$hash.fits").writeBytes(byteArrayOf(9))
 check(runCatching { h.recoverPrefix(id) }.isFailure)
 root.resolve("fits/$hash.fits").writeBytes(byteArrayOf(1,2,3))
 root.resolve("$id.v2.csv").writeText(recovered.rows.joinToString("\n", postfix="\n"))
 check(h.verify(id).size == 2)
 root.resolve("$id.v2.csv").appendText("\n")
 check(runCatching { h.recoverPrefix(id) }.isFailure)
 println("13 checks passed")
 } finally {root.deleteRecursively()}
}
