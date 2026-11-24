package com.synthesis.engine.discovery

import com.synthesis.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Estimates the commercial value of a Discovery: market sizing (TAM/SAM/SOM),
 * licensing potential, and plausible industry applications, derived from the
 * domains involved and the discovery's impact/patentability scores.
 */
@Singleton
class CommercialValueEstimator @Inject constructor() {

    // Unavailable until independently validated evidence and methodology exist.
    fun estimate(discovery: Discovery, patentAssessment: PatentAssessment): CommercialEstimate? = null
}
