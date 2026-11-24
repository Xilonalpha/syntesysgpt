package com.synthesis.service.worker

/** Fetch completeness only; not proof of scientific validity or freshness. */
object SyncOutcome {
    fun status(received: Int, requested: Int, catalogReceived: Boolean): String {
        require(requested > 0 && received in 0..requested)
        return when {
            received == requested && catalogReceived -> "SUCCESS"
            received == 0 && !catalogReceived -> "FAILED"
            else -> "PARTIAL"
        }
    }
}
