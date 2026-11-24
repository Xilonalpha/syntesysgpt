package com.synthesis.service

import com.synthesis.domain.model.exoplanet.PhotometricDataPoint

/** Replays inputs only for the exact supported pipeline revision. */
class ExperimentReplay(private val manifests: ExperimentManifestStore, private val evidence: FitsEvidenceStore) {
    companion object { const val ALGORITHM = "BLS-local56-secondary-baseline" }
    data class Sector(val tic: Long, val mission: String, val sector: Int, val points: List<PhotometricDataPoint>)
    fun load(id: String, decode: (ByteArray) -> Sector): List<PhotometricDataPoint> {
        val manifest = manifests.readVerified(id)
        require(manifest.algorithm == ALGORITHM) { "Versiune algoritm incompatibilă" }
        val points = mutableListOf<PhotometricDataPoint>()
        for (source in manifest.sources) {
            val decoded = decode(evidence.readVerified(source.sha256))
            require(decoded.tic == manifest.tic && decoded.mission.equals("TESS", true))
            require(decoded.sector == source.sector && decoded.points.size == source.points)
            require(decoded.points.size <= 250_000 - points.size)
            points.addAll(decoded.points)
        }
        return mergeMastSectorPoints(points)
    }
}
