package com.synthesis.engine.validation

/** Exploratory evidence bookkeeping. Distinct acquisitions do not prove independence. */
object ResearchEvidenceLedger {
    enum class Outcome { SUPPORTS, CONTRADICTS, INCONCLUSIVE }
    data class Evidence(val targetKey: String, val hypothesisKey: String, val acquisitionId: String,
        val productSha256: String, val method: String, val outcome: Outcome)
    data class Summary(val distinctProducts: Int, val distinctAcquisitions: Int,
        val supportingAcquisitions: Int, val contradictingAcquisitions: Int,
        val inconclusiveAcquisitions: Int)
    fun summarize(entries: List<Evidence>): Summary {
        require(entries.isNotEmpty())
        require(entries.all { it.targetKey.isNotBlank() && it.hypothesisKey.isNotBlank() &&
            it.acquisitionId.isNotBlank() && it.method.isNotBlank() &&
            it.productSha256.matches(Regex("[0-9a-fA-F]{64}")) })
        require(entries.map { it.targetKey }.distinct().size == 1)
        require(entries.map { it.hypothesisKey }.distinct().size == 1)
        val products = entries.groupBy { it.productSha256.lowercase(java.util.Locale.ROOT) }
        require(products.values.all { rows -> rows.map { it.acquisitionId }.distinct().size == 1 })
        val acquisitions = entries.groupBy { it.acquisitionId }
        val outcomes = acquisitions.values.map { rows ->
            val unique = rows.map { it.outcome }.distinct()
            if (unique.size == 1) unique.single() else Outcome.INCONCLUSIVE
        }
        return Summary(products.size, acquisitions.size, outcomes.count { it == Outcome.SUPPORTS },
            outcomes.count { it == Outcome.CONTRADICTS }, outcomes.count { it == Outcome.INCONCLUSIVE })
    }
}
