import com.synthesis.service.FitsEvidenceStore
import java.io.File
fun main() {
    val dir = java.nio.file.Files.createTempDirectory("fits-test").toFile()
    try {
        val store = FitsEvidenceStore(dir)
        val bytes = byteArrayOf(1,2,3)
        val hash = store.save(bytes)
        check(store.readVerified(hash).contentEquals(bytes))
        check(store.save(bytes) == hash && dir.listFiles()!!.size == 1)
        File(dir, "$hash.fits").writeBytes(byteArrayOf(4))
        check(runCatching { store.readVerified(hash) }.isFailure)
        check(runCatching { store.readVerified("../bad") }.isFailure)
        println("4 checks passed")
    } finally { dir.deleteRecursively() }
}
