import com.synthesis.service.*
import java.nio.file.Files
fun main() {
 val root=Files.createTempDirectory("archive22").toFile()
 try {
 val evidence=FitsEvidenceStore(root.resolve("fits"), 4)
 val hash=evidence.save(byteArrayOf(1,2,3))
 check(evidence.save(byteArrayOf(1,2,3))==hash)
 check(runCatching { evidence.save(byteArrayOf(4,5)) }.isFailure)
 val store=ExperimentManifestStore(root.resolve("manifest"), evidence)
 val source=ExperimentManifestStore.Source("mast:TESS/product/test_lc.fits",hash,1,8)
 val id=store.save(123,listOf(source),"BLS-test")
 check(store.readVerified(id).sources==listOf(source))
 val f=root.resolve("manifest/$id.manifest")
 val original=f.readText(); f.writeText(original.replace("tic=123","tic=124"))
 check(runCatching { store.readVerified(id) }.isFailure)
 f.writeText(original)
 check(runCatching { store.readVerified("../bad") }.isFailure)
 root.resolve("fits/$hash.fits").writeBytes(byteArrayOf(9))
 check(runCatching { store.readVerified(id) }.isFailure)
 println("6 checks passed")
 } finally { root.deleteRecursively() }
}
