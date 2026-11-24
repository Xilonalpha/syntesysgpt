package com.synthesis.service

import com.synthesis.domain.model.exoplanet.PhotometricDataPoint

/** Inputs are individually normalized FITS light curves, not phase-folded data. */
internal fun mergeMastSectorPoints(input: List<PhotometricDataPoint>): List<PhotometricDataPoint> {
    require(input.size >= 8) { "Fotometrie insuficientă" }
    require(input.all { it.bjdTime.isFinite() && it.bjdTime > 2400000.0 &&
        it.relativeFlux.isFinite() && it.fluxError.isFinite() && it.fluxError > 0.0 })
    val sorted = input.sortedBy { it.bjdTime }
    require(sorted.zipWithNext().all { (a, b) -> a.bjdTime < b.bjdTime }) {
        "Cadente duplicate; selecție de produs necesară"
    }
    val origin = sorted.first().bjdTime
    return sorted.map { it.copy(minuteOffset = (it.bjdTime - origin) * 1440.0) }
}
