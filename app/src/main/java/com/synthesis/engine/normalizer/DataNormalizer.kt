package com.synthesis.engine.normalizer

import com.synthesis.domain.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataNormalizer @Inject constructor() {

    data class NormalizedRecord(
        val id: String,
        val entity: String,
        val timestamp: Long,
        val location: GeoLocation?,
        val measurement: Double,
        val unit: String,
        val uncertainty: Float,
        val source: String,
        val observationType: ObservationType
    )

    fun normalize(observation: Observation): NormalizedRecord {
        val origUnit = standardizeUnit(observation.measurement.unit)
        // Standardize measurements: Convert °F to °C for consistent thermodynamic comparison
        val (normVal, normUnit) = when (origUnit) {
            "°F" -> ((observation.measurement.value - 32.0) * 5.0 / 9.0) to "°C"
            "hPa", "millibar", "mb" -> (observation.measurement.value / 10.0) to "kPa"
            else -> observation.measurement.value to origUnit
        }

        return NormalizedRecord(
            id = observation.id,
            entity = observation.entity,
            timestamp = observation.timestamp,
            location = observation.location,
            measurement = normVal,
            unit = normUnit,
            uncertainty = observation.uncertainty * when (origUnit) { "°F" -> 5f / 9f; "hPa" -> 0.1f; else -> 1f },
            source = observation.sourceName,
            observationType = observation.observationType
        )
    }

    fun normalizeBatch(observations: List<Observation>): List<NormalizedRecord> {
        return observations.map { normalize(it) }
    }

    private fun standardizeUnit(unit: String): String {
        val trimmed = unit.trim()
        if (trimmed in setOf("MW", "mW", "Mw", "M", "C", "F")) return trimmed
        return when (trimmed.lowercase(java.util.Locale.ROOT)) {
            "days", "day", "d" -> "days"
            "hours", "hour", "h" -> "hours"
            "km", "kilometers", "kilometer" -> "km"
            "m", "meters", "meter" -> "m"
            "deg", "degrees", "degree" -> "deg"
            "magnitude" -> "magnitude"
            "celsius", "°c" -> "°C"
            "fahrenheit", "°f" -> "°F"
            "hpa", "millibar", "mb" -> "hPa"
            "%", "percent", "percentage" -> "%"
            "m/s", "mps", "meters per second" -> "m/s"
            else -> trimmed
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(timestamp))
    }

    fun calculateDistance(loc1: GeoLocation, loc2: GeoLocation): Double {
        require(listOf(loc1, loc2).all { it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 })
        val R = 6371.0 // Earth's radius in km
        val lat1 = Math.toRadians(loc1.latitude)
        val lat2 = Math.toRadians(loc2.latitude)
        val dLat = Math.toRadians(loc2.latitude - loc1.latitude)
        val dLon = Math.toRadians(loc2.longitude - loc1.longitude)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val bounded = a.coerceIn(0.0, 1.0)
        val c = 2 * Math.atan2(Math.sqrt(bounded), Math.sqrt(1 - bounded))

        return R * c
    }
}
