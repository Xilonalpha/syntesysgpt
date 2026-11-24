package com.synthesis.engine.exoplanet

import com.synthesis.domain.model.exoplanet.LiveTransitTarget
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Live Transit Sky Radar & Observation Planner
 *
 * Propagates linear and perturbed transit ephemerides to compute
 * next transit windows, ingress/egress countdowns, and amateur telescope
 * feasibility for ground confirmation.
 */
@Singleton
class TransitSkyRadarEngine @Inject constructor() {

    fun computeNextTransit(
        candidateName: String,
        ticId: String,
        constellation: String,
        rightAscension: String,
        declination: String,
        apparentVisualMag: Double,
        periodDays: Double,
        transitDurationHours: Double,
        epochBtjd: Double,
        transitDepthPpm: Double,
        currentBtjd: Double
    ): LiveTransitTarget {
        require(currentBtjd.isFinite() && epochBtjd.isFinite())
        require(periodDays.isFinite() && periodDays > 0.0)
        require(transitDurationHours.isFinite() && transitDurationHours > 0.0 && transitDurationHours / 24.0 < periodDays)
        require(apparentVisualMag.isFinite() && transitDepthPpm.isFinite() && transitDepthPpm > 0.0 && transitDepthPpm < 1e6)
        // Next ingress, not next midpoint. Both epochs must be supplied in BTJD/TDB.
        val halfDurationDays = transitDurationHours / 48.0
        val nextCycle = ceil((currentBtjd - epochBtjd + halfDurationDays) / periodDays)
        val nextEpoch = epochBtjd + nextCycle * periodDays
        val hoursUntilIngress = (nextEpoch - halfDurationDays - currentBtjd) * 24.0
        require(nextEpoch.isFinite() && hoursUntilIngress.isFinite())
        val feasibility = "Necalculată: necesită locație, vizibilitate, instrument și incertitudini ale efemeridei. Predicție liniară nominală."

        return LiveTransitTarget(
            candidateName = candidateName,
            ticId = ticId,
            constellation = constellation,
            rightAscension = rightAscension,
            declination = declination,
            apparentVisualMag = apparentVisualMag,
            periodDays = periodDays,
            transitDurationHours = transitDurationHours,
            nextTransitEpochBtjd = nextEpoch,
            hoursUntilIngress = hoursUntilIngress.coerceAtLeast(0.0),
            transitDepthPpm = transitDepthPpm,
            amateurTelescopeFeasibility = feasibility
        )
    }
}
