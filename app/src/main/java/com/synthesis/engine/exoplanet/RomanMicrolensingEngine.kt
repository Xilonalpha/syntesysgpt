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
        val span = event.einsteinCrossingTimeDays * 1.5
        val dt = (2.0 * span) / steps.toDouble()

        for (i in 0..steps) {
            val t = -span + i * dt
            val u = sqrt(event.impactParameterU0.pow(2.0) + (t / event.einsteinCrossingTimeDays).pow(2.0))
            val baseA = paczynskiMagnification(u)

            points.add(
                MicrolensingCurvePoint(
                    timeDays = t,
                    magnification = baseA,
                    planetaryAnomalyDeltaA = 0.0,
                    isCausticCrossing = false
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
            // Einstein radius assumed to describe total binary lens mass.
            val totalMassSolar = (thetaEMas.pow(2.0)) / (kappa * (piRel * 1000.0))
            totalMassSolar * (requireNotNull(event.massRatioQ) / (1.0 + event.massRatioQ))
        }

        require(massSolar.isFinite() && massSolar > 0)
        val massEarth = massSolar * 333000.0
        require(massEarth.isFinite())
        val massJupiter = massEarth / 317.8

        val crossingHours = event.einsteinCrossingTimeDays * 24.0

        val causticStructure = "Not assessed: point-lens approximation only"

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
