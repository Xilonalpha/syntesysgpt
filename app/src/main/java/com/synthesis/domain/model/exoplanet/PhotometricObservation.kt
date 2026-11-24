package com.synthesis.domain.model.exoplanet

data class PhotometricDataPoint(
    val minuteOffset: Double,      // Minutes from observation start
    val bjdTime: Double,          // Barycentric Julian Date
    val relativeFlux: Double,      // Normalized stellar flux (baseline = 1.000)
    val fluxError: Double = Double.NaN // Photometric uncertainty
)

data class LiveObservationSession(
    val candidateName: String = "Necunoscut",
    val hostStar: String = "Necunoscut",
    val observerName: String = "Necunoscut",
    val aavsoObserverCode: String = "Necunoscut",
    val observatoryLocation: String = "Necunoscut",
    val telescopeSpecs: String = "Necunoscut",
    val cameraModel: String = "Necunoscut",
    val filterBand: String = "Necunoscut",
    val comparisonStarName: String = "Necunoscut",
    val comparisonStarMag: Double = Double.NaN,
    val checkStarName: String = "Necunoscut",
    val checkStarMag: Double = Double.NaN,
    val expectedDepthPpm: Double = Double.NaN,
    val expectedDurationMinutes: Double = Double.NaN,
    val dataPoints: List<PhotometricDataPoint> = emptyList(),
    val isObserving: Boolean = false,
    val isRealImportedData: Boolean = false,
    val dataSourceTag: String = "Fără observații",
    val currentPhase: String = "Necunoscut",
    val calculatedMidTransitBjd: Double = Double.NaN,
    val measuredDepthPpm: Double = Double.NaN,
    val measuredRadiusEarth: Double = Double.NaN,
    val signalToNoiseRatio: Double = Double.NaN,
    val chiSquaredPerDof: Double = Double.NaN
)
