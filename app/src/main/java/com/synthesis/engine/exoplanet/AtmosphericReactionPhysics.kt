package com.synthesis.engine.exoplanet

import kotlin.math.ln

data class ReactionSpecies(val stoichiometricCoefficient: Double, val activity: Double)
data class ReactionEnergy(val logReactionQuotient: Double, val gibbsJPerMol: Double)

/** Reaction-specific calculation, not a life probability or global equilibrium model.
 * Activities are dimensionless; standard Gibbs energy must correspond to the supplied temperature.
 */
object AtmosphericReactionPhysics {
    fun evaluate(temperatureK: Double, standardGibbsJPerMol: Double,
                 species: List<ReactionSpecies>): ReactionEnergy {
        require(temperatureK.isFinite() && temperatureK > 0 && standardGibbsJPerMol.isFinite())
        require(species.size >= 2 && species.any { it.stoichiometricCoefficient < 0 } &&
            species.any { it.stoichiometricCoefficient > 0 })
        require(species.all { it.stoichiometricCoefficient.isFinite() &&
            it.stoichiometricCoefficient != 0.0 && it.activity.isFinite() && it.activity > 0 })
        val logQ = species.sumOf { it.stoichiometricCoefficient * ln(it.activity) }
        val gibbs = standardGibbsJPerMol + 8.314462618 * temperatureK * logQ
        require(logQ.isFinite() && gibbs.isFinite())
        return ReactionEnergy(logQ, gibbs)
    }
}
