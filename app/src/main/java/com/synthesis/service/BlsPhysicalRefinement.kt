package com.synthesis.service

import com.synthesis.domain.model.exoplanet.PeriodicTransitDetectionResult
import kotlin.math.*

/** Optional post-BLS refinement, not independent detection or confirmation. */
object BlsPhysicalRefinement {
    data class Result(val geometry: TransitGeometryFit.Result, val boxChiSquare: Double, val flatChiSquare: Double, val boxBic: Double, val physicalBic: Double, val flatBic: Double)
    fun refine(bls: PeriodicTransitDetectionResult, points: List<PhysicalTransitFit.Point>, a: Double, maxImpact: Double, u1: Double, u2: Double): Result {
        require(bls.isDetected) { "No accepted BLS candidate" }
        return refineSeed(points, bls.bestPeriodDays, bls.epochBjd, bls.transitDurationHours/24, a,maxImpact,u1,u2)
    }
    internal fun exposureFraction(time: Double, epoch: Double, period: Double, duration: Double, exposure: Double): Double {
        val phase = Math.IEEEremainder(time-epoch, period)
        if (exposure == 0.0) return if (abs(phase) <= duration/2) 1.0 else 0.0
        return (-1..1).sumOf { j ->
            val center = j*period
            (min(phase+exposure/2,center+duration/2)-max(phase-exposure/2,center-duration/2)).coerceAtLeast(0.0)
        }.div(exposure).coerceIn(0.0,1.0)
    }
    internal fun refineSeed(points: List<PhysicalTransitFit.Point>, period: Double, epoch: Double, duration: Double, a: Double, maxImpact: Double, u1: Double, u2: Double): Result {
        require(period.isFinite() && period > 0 && duration.isFinite() && duration > 0 && duration < period/2)
        require(points.size in 8..2000 && points.all { it.time.isFinite() && it.flux.isFinite() && it.sigma.isFinite() && it.sigma > 0 && it.exposure.isFinite() && it.exposure in 0.0..period }) { "Invalid photometric samples or exposure" }
        val geometry = TransitGeometryFit.fit(points,period,epoch,duration/2,a,maxImpact,u1,u2)
        val fractions = points.map { exposureFraction(it.time,epoch,period,duration,it.exposure) }
        val s = points.minOf { it.sigma }
        val w = points.map { (s/it.sigma).pow(2) }
        val total = w.sum()
        val flat = points.indices.sumOf { w[it]*points[it].flux } / total
        val fmean = points.indices.sumOf { w[it]*fractions[it] } / total
        val variance = points.indices.sumOf { w[it]*(fractions[it]-fmean).pow(2) }
        require(variance > 0 && variance.isFinite()) { "Unidentifiable box depth" }
        val depth = (points.indices.sumOf { w[it]*(fmean-fractions[it])*(points[it].flux-flat) } / variance).coerceAtLeast(0.0)
        val baseline = flat + depth*fmean
        val nullChi = points.sumOf { ((it.flux-flat)/it.sigma).pow(2) }
        val box = points.indices.sumOf { ((points[it].flux-baseline+depth*fractions[it])/points[it].sigma).pow(2) }
        require(box.isFinite() && nullChi.isFinite())
        val penalty = ln(points.size.toDouble())
        return Result(geometry,box,nullChi,box+4*penalty,geometry.fit.chiSquare+4*penalty,nullChi+penalty)
    }
}
