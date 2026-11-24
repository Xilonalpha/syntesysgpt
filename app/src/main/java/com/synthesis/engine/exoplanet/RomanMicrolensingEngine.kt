package com.synthesis.engine.exoplanet

import com.synthesis.domain.model.exoplanet.MicrolensingCurvePoint
import com.synthesis.domain.model.exoplanet.MicrolensingEvent
import com.synthesis.domain.model.exoplanet.MicrolensingInversionResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class RomanMicrolensingEngine @Inject constructor() {

    fun getBenchmarkEvents(): List<MicrolensingEvent> {
        // Legacy examples lack verified observational provenance.
        return emptyList()
    }

    /**
     * Computes the Paczynski (1986) point-source point-lens magnification
     * A(u) = (u^2 + 2) / (u * sqrt(u^2 + 4))
     */
    fun paczynskiMagnification(u: Double): Double {
        val uSafe = max(1e-5, abs(u))
        val u2 = uSafe * uSafe
        return (u2 + 2.0) / (uSafe * sqrt(u2 + 4.0))
    }

    /**
     * Simulates light curve across normalized time span t in [-1.5 * t_E, +1.5 * t_E]
     */
    fun generateLightCurve(
        event: MicrolensingEvent,
        steps: Int = 100
    ): List<MicrolensingCurvePoint> {
        require(steps in 2..10000)
        require(event.einsteinCrossingTimeDays.isFinite() && event.einsteinCrossingTimeDays > 0)
        require(event.impactParameterU0.isFinite() && event.impactParameterU0 > 0)
        val points = mutableListOf<MicrolensingCurvePoint>()
        val span = max(0.1, event.einsteinCrossingTimeDays * 1.5)
        val dt = (2.0 * span) / steps.toDouble()

        // If binary lens, planetary anomaly time & width
        val anomalyTime = if (!event.isFreeFloatingPlanet && event.separationS != null) {
            span * 0.25 // slight offset after peak
        } else null

        val anomalyDuration = if (event.massRatioQ != null) {
            max(0.01, event.einsteinCrossingTimeDays * sqrt(event.massRatioQ))
        } else 0.05

        for (i in 0..steps) {
            val t = -span + i * dt
            val u = sqrt(event.impactParameterU0.pow(2.0) + (t / max(0.001, event.einsteinCrossingTimeDays)).pow(2.0))
            val baseA = paczynskiMagnification(u)

            var deltaA = 0.0
            var isCaustic = false

            if (anomalyTime != null && event.massRatioQ != null) {
                val distToAnomaly = abs(t - anomalyTime)
                if (distToAnomaly < anomalyDuration * 2.0) {
                    val gaussianAnomaly = exp(-0.5 * (distToAnomaly / (anomalyDuration * 0.5)).pow(2.0))
                    deltaA = baseA * 0.35 * gaussianAnomaly
                    if (distToAnomaly < anomalyDuration * 0.6) {
                        isCaustic = true
                    }
                }
            }

            points.add(
                MicrolensingCurvePoint(
                    timeDays = (t * 1000.0).roundToLong() / 1000.0,
                    magnification = ((baseA + deltaA) * 1000.0).roundToLong() / 1000.0,
                    planetaryAnomalyDeltaA = (deltaA * 1000.0).roundToLong() / 1000.0,
                    isCausticCrossing = isCaustic
                )
            )
        }

        return points
    }

    /**
     * Solves the inverse problem: inverts the Einstein ring radius theta_E and crossing time t_E
     * to determine the rogue planet or companion mass in Earth and Jupiter masses.
     */
    fun invertMicrolensingEvent(
        event: MicrolensingEvent
    ): MicrolensingInversionResult {
        require(event.lensDistanceKpc.isFinite() && event.sourceDistanceKpc.isFinite() && event.lensDistanceKpc > 0 && event.sourceDistanceKpc > event.lensDistanceKpc)
        require(event.einsteinCrossingTimeDays.isFinite() && event.einsteinCrossingTimeDays > 0)
        require(event.isFreeFloatingPlanet || (event.massRatioQ?.let { it.isFinite() && it > 0 } == true))
        val dl = event.lensDistanceKpc * 1000.0 // pc
        val ds = event.sourceDistanceKpc * 1000.0 // pc
        val piRel = (1.0 / dl) - (1.0 / ds) // relative parallax (arcsec)

        // Typical galactic bulge relative proper motion mu_rel ~ 6.5 mas/yr
        val muRelMasYr = 6.5
        val muRelMasDay = muRelMasYr / 365.25

        // theta_E = mu_rel * t_E
        val thetaEMas = muRelMasDay * event.einsteinCrossingTimeDays
        val thetaEArcsec = thetaEMas / 1000.0

        // Mass of lens: M = theta_E^2 / (kappa * pi_rel), where kappa = 4G / (c^2 * 1AU) ~ 8.144 mas / M_sun
        val kappa = 8.144 // mas / M_sun
        val massSolar = if (event.isFreeFloatingPlanet) {
            // Lens itself is the planet
            (thetaEMas.pow(2.0)) / (kappa * (piRel * 1000.0))
        } else {
            // Primary lens is a star, planet is q * M_*
            val primaryMassSolar = (thetaEMas.pow(2.0)) / (kappa * (piRel * 1000.0))
            primaryMassSolar * requireNotNull(event.massRatioQ)
        }

        val massEarth = massSolar * 333000.0
        val massJupiter = massEarth / 317.8

        val crossingHours = event.einsteinCrossingTimeDays * 24.0

        val causticStructure = if (event.isFreeFloatingPlanet) {
            "Punctiform Izolat (Fără structură de caustică stelară - Planetă Orfană FFP)"
        } else {
            val s = event.separationS ?: 1.0
            if (s > 1.0) "Caustice Planetare Triangulare (Separare Largă s = ${String.format("%.2f", s)})"
            else "Caustică Centrală Închisă (Separare Strânsă s = ${String.format("%.2f", s)})"
        }

        val summary = "Conditional mass estimate assuming 6.5 mas/yr proper motion and supplied distances. Synthetic illustrative curve; no planet confirmation or binary-lens fit."

        val curve = generateLightCurve(event)

        return MicrolensingInversionResult(
            eventId = event.eventId,
            targetName = event.targetName,
            lensType = event.lensType,
            inferredMassEarth = (massEarth * 100.0).roundToLong() / 100.0,
            inferredMassJupiter = (massJupiter * 1000.0).roundToLong() / 1000.0,
            einsteinRadiusMas = (thetaEMas * 1000.0).roundToLong() / 1000.0,
            properMotionMasYr = muRelMasYr,
            peakMagnification = (event.peakMagnificationA0 * 100.0).roundToLong() / 100.0,
            crossingDurationHours = (crossingHours * 10.0).roundToLong() / 10.0,
            isFreeFloating = event.isFreeFloatingPlanet,
            causticStructure = causticStructure,
            astrophysicalSummary = summary,
            lightCurve = curve
        )
    }
}
