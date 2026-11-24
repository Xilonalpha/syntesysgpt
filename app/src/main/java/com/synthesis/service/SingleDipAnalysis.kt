package com.synthesis.service

import com.synthesis.domain.model.exoplanet.PhotometricDataPoint
import kotlin.math.sqrt

data class SingleDipEstimate(
    val depthPpm: Double,
    val depthErrorPpm: Double,
    val midpointBjd: Double,
    val sampledSpanMinutes: Double,
    val localSnr: Double
)

/** Evaluates a preselected window, not a blind search or planetary classification. */
object SingleDipAnalysis {
    fun estimate(points: List<PhotometricDataPoint>, startBjd: Double, endBjd: Double): SingleDipEstimate? {
        if (!startBjd.isFinite() || !endBjd.isFinite() || startBjd >= endBjd) return null
        if (points.any { !it.bjdTime.isFinite() || !it.relativeFlux.isFinite() || !it.fluxError.isFinite() || it.fluxError <= 0 }) return null
        val sorted = points.sortedBy { it.bjdTime }
        if (sorted.zipWithNext().any { (a,b) -> a.bjdTime >= b.bjdTime }) return null
        val before = sorted.filter { it.bjdTime < startBjd }
        val after = sorted.filter { it.bjdTime > endBjd }
        val inside = sorted.filter { it.bjdTime in startBjd..endBjd }
        if (before.size < 3 || after.size < 3 || inside.size < 3) return null
        fun stats(group: List<PhotometricDataPoint>): Pair<Double, Double> {
            val mean = group.map { it.relativeFlux }.average()
            val n = group.size.toDouble()
            val variance = group.sumOf { (it.relativeFlux - mean) * (it.relativeFlux - mean) } / (n - 1)
            val reported = group.sumOf { it.fluxError * it.fluxError } / (n*n)
            return mean to maxOf(variance/n, reported)
        }
        val (left, lv) = stats(before)
        val (right, rv) = stats(after)
        if (kotlin.math.abs(left-right) > 3*sqrt(lv+rv)) return null
        val (base, bv) = stats(before + after)
        val (dip, dv) = stats(inside)
        if (base <= 0 || dip <= 0 || dip >= base) return null
        val depth = 1 - dip/base
        val error = sqrt(dv/(base*base) + dip*dip*bv/(base*base*base*base))
        if (!error.isFinite() || error <= 0 || !depth.isFinite()) return null
        return SingleDipEstimate(depth*1e6, error*1e6,
            inside.first().bjdTime + (inside.last().bjdTime-inside.first().bjdTime)/2,
            (inside.last().bjdTime-inside.first().bjdTime)*1440, depth/error)
    }
}
