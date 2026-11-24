import com.synthesis.service.worker.SyncOutcome
fun main() {
    check(SyncOutcome.status(4,4,true) == "SUCCESS")
    check(SyncOutcome.status(0,4,false) == "FAILED")
    check(SyncOutcome.status(2,4,true) == "PARTIAL")
    check(SyncOutcome.status(4,4,false) == "PARTIAL")
    check(SyncOutcome.status(0,4,true) == "PARTIAL")
    check(runCatching { SyncOutcome.status(5,4,true) }.isFailure)
    println("6 checks passed")
}
