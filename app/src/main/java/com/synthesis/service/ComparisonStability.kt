package com.synthesis.service

import com.synthesis.domain.model.exoplanet.DetectedStar
import kotlin.math.*

/** Preliminary ensemble consistency gate, not a proof of stellar stability. */
class ComparisonStability {
    private var baseline: List<DetectedStar>? = null
    fun reset() { baseline = null }
    fun accept(stars: List<DetectedStar>): Boolean {
        if (stars.size < 2 || stars.map { it.id }.distinct().size != stars.size ||
            stars.any { it.isSaturated || !it.netFlux.isFinite() || it.netFlux <= 0 || !it.snr.isFinite() || it.snr <= 0 }) return false
        val sorted = stars.sortedBy { it.id }
        val ref = baseline ?: run { baseline = sorted.toList(); return true }
        if (ref.map { it.id } != sorted.map { it.id }) return false
        for (i in sorted.indices) for (j in 0 until i) {
            val delta = ln(sorted[i].netFlux) - ln(sorted[j].netFlux) - ln(ref[i].netFlux) + ln(ref[j].netFlux)
            val sigma = sqrt(listOf(sorted[i], sorted[j], ref[i], ref[j]).sumOf { (1.0 / it.snr).pow(2) })
            if (!delta.isFinite() || !sigma.isFinite() || abs(delta) > max(0.005, 5 * sigma)) return false
        }
        return true
    }
}
