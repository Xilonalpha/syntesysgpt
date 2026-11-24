package com.synthesis.engine.exoplanet

import com.synthesis.domain.model.exoplanet.DilutionRescueCandidate
import javax.inject.Inject
import javax.inject.Singleton

/** Aperture dilution requires measured same-band fluxes, not Gaia magnitude alone. */
@Singleton
class DilutionDeBlenderEngine @Inject constructor() {
    fun deblendCandidate(
        candidateName: String,
        ticId: String,
        originalNasaStatus: String,
        observedTransitDepthPpm: Double,
        targetStarMagGaia: Double,
        blendingStarMagGaia: Double,
        angularSeparationArcsec: Double,
        stellarRadiusSun: Double
    ): DilutionRescueCandidate {
        throw UnsupportedOperationException("Measured same-band aperture fluxes and uncertainties required; magnitude-only correction removed.")
    }

    fun deblendMeasured(
        observedTransitDepthPpm: Double,
        depthErrorPpm: Double,
        targetFlux: Double,
        targetError: Double,
        contaminantFlux: Double,
        contaminantError: Double
    ): ApertureDilutionEstimate = ApertureDilutionPhysics.estimate(
        observedTransitDepthPpm, depthErrorPpm, targetFlux, targetError,
        contaminantFlux, contaminantError
    )
}
