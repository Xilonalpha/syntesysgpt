package com.synthesis.engine.correlation
import com.synthesis.domain.model.*
import com.synthesis.engine.normalizer.DataNormalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*
@Singleton
class CorrelationEngine @Inject constructor(private val normalizer: DataNormalizer) {
    fun findCorrelations(observations: List<Observation>) = correlate(observations, observations.firstOrNull()?.investigationId ?: "")
    fun correlate(observations: List<Observation>, investigationId: String): List<Correlation> {
        if (observations.any { it.investigationId != investigationId }) return emptyList()
        val groups = observations.distinctBy { it.id }.filter { it.measurement.value.isFinite() }
            .groupBy { listOf(it.sourceId, it.entity, it.observationType.name, normalizer.normalize(it).unit) }.values.toList()
        val result = mutableListOf<Correlation>()
        for (i in groups.indices) for (j in i+1 until groups.size) {
            val a = groups[i]; val b = groups[j]
            if (a.first().sourceId == b.first().sourceId) continue
            fun bins(x: List<Observation>) = x.groupBy { Math.floorDiv(it.timestamp, 86400000L) }
                .mapValues { (_, v) -> v.map { normalizer.normalize(it).measurement }.average() }
            val x = bins(a); val y = bins(b); val days = x.keys.intersect(y.keys).sorted()
            if (days.size < 10) continue
            val u = days.map { x.getValue(it) }; val v = days.map { y.getValue(it) }
            val mu = u.average(); val mv = v.average()
            val du = u.map { it-mu }; val dv = v.map { it-mv }
            val denom = sqrt(du.sumOf { it*it }) * sqrt(dv.sumOf { it*it })
            if (!denom.isFinite() || denom <= 0) continue
            val rawR = du.indices.sumOf { du[it]*dv[it] } / denom
            if (!rawR.isFinite()) continue
            val r = rawR.coerceIn(-1.0,1.0)
            if (!r.isFinite()) continue
            result.add(Correlation(java.util.UUID.randomUUID().toString(), investigationId,
                a.first().sourceName, b.first().sourceName, CorrelationType.STATISTICAL, abs(r).toFloat(),
                "Exploratory daily-bin Pearson r=$r; n=${days.size}; significance not assessed; causality not established. Entities: ${a.first().entity}, ${b.first().entity}.", false,
                listOf(a.first().observationType.name,b.first().observationType.name)))
        }
        return result.sortedByDescending { it.strength }
    }
}
