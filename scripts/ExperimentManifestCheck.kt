import com.synthesis.service.*
import java.nio.file.Files
fun main() {
    val root = Files.createTempDirectory("manifest-check").toFile()
    try {
        val evidence = FitsEvidenceStore(root.resolve("fits"))
        val hash = evidence.save(byteArrayOf(1,2,3))
        val store = ExperimentManifestStore(root.resolve("manifests"), evidence)
        val source = ExperimentManifestStore.Source("mast:TESS/product/test_lc.fits", hash, 1, 8)
        val id = store.save(123, listOf(source), "BLS-patch09")
        check(root.resolve("manifests/$id.manifest").readText().contains(hash))
        check(runCatching { store.save(123, listOf(source, source), "BLS-patch09") }.isFailure)
        check(runCatching { store.save(123, listOf(source.copy(uri="invalid")), "BLS-patch09") }.isFailure)
        root.resolve("fits/$hash.fits").writeBytes(byteArrayOf(9))
        check(runCatching { store.save(123, listOf(source), "BLS-patch09") }.isFailure)
        println("4 checks passed")
    } finally { root.deleteRecursively() }
}
