package com.synthesis.engine.exoplanet

import kotlin.math.*

/** Descriptive residual diagnostics, not a calibrated false-alarm probability. */
object CorrelatedNoisePhysics {
    data class Diagnostic(val lagOneCorrelation: Double, val blockNoiseRatio: Double,
        val adjacentPairs: Int, val completeBlocks: Int)

    fun estimate(times: List<Double>, residuals: List<Double>, cadenceDays: Double,
        blockSize: Int): Diagnostic? {
        require(times.size == residuals.size && cadenceDays.isFinite() && cadenceDays > 0)
        require(blockSize in 2..1024)
        require(times.all { it.isFinite() } && residuals.all { it.isFinite() })
        require(times.zipWithNext().all { (a,b) -> b > a })
        if (times.size < 20) return null
        val mean = residuals.average()
        val centered = residuals.map { it - mean }
        val variance = centered.map { it * it }.average()
        if (!variance.isFinite() || variance <= 0) return null
        val pairs = (1 until times.size).filter {
            abs((times[it] - times[it-1]) / cadenceDays - 1) <= 0.2
        }
        if (pairs.size < 10) return null
        val left = pairs.sumOf { centered[it-1].pow(2) }
        val right = pairs.sumOf { centered[it].pow(2) }
        if (left <= 0 || right <= 0) return null
        val rho = pairs.sumOf { centered[it-1] * centered[it] } / (sqrt(left) * sqrt(right))
        val blocks = mutableListOf<Double>()
        var start = 0
        while (start + blockSize <= times.size) {
            val end = start + blockSize
            val gap = (start+1 until end).firstOrNull {
                abs((times[it]-times[it-1])/cadenceDays - 1) > 0.2
            }
            if (gap != null) { start = gap; continue }
            blocks.add(centered.subList(start,end).average())
            start = end
        }
        if (blocks.size < 5) return null
        val blockMean = blocks.average()
        val blockVariance = blocks.sumOf { (it-blockMean).pow(2) } / (blocks.size-1)
        val ratio = sqrt(blockVariance / variance * blockSize)
        if (!rho.isFinite() || !ratio.isFinite()) return null
        return Diagnostic(rho.coerceIn(-1.0,1.0), ratio, pairs.size, blocks.size)
    }
}
