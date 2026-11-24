import com.synthesis.service.*
import java.nio.file.Files
import java.io.File
fun main() {
 val root = Files.createTempDirectory("result-check").toFile()
 try {
 val evidence = FitsEvidenceStore(File(root,"fits")); val hash = evidence.save(byteArrayOf(1,2,3))
 val manifests = ExperimentManifestStore(File(root,"manifest"), evidence)
 val source = ExperimentManifestStore.Source("mast:TESS/product/test_lc.fits",hash,1,8)
 val id = manifests.save(1,listOf(source),"test")
 val store = ExperimentResultStore(File(root,"results"),manifests)
 val a = ExperimentResultStore.Metrics(true,listOf(1.0,2.0,3.0,4.0,null,6.0))
 val run = store.save(id,a); check(store.read(id,run)==a)
 val second = store.save(id,a); check(run!=second && ExperimentResultStore.compare(store.read(id,run),store.read(id,second)).isEmpty())
 check(ExperimentResultStore.compare(a,a.copy(detected=false,values=listOf(2.0,2.0,3.0,4.0,null,6.0)))==listOf("detected","period_days"))
 val other=manifests.save(2,listOf(source),"test"); check(runCatching { store.read(other,run) }.isFailure)
 File(root,"results/$run.result").appendText("tampered"); check(runCatching { store.read(id,run) }.isFailure)
 check(store.history(id).runs == listOf(second) && store.history(id).rejected == listOf("$run.result"))
 check(store.history(other).runs.isEmpty())
 val limited = ExperimentResultStore(File(root,"limited"),manifests,1)
 check(runCatching { limited.save(id,a) }.isFailure && limited.history(id).runs.isEmpty())
 println("8 checks passed")
 } finally { root.deleteRecursively() }
}
