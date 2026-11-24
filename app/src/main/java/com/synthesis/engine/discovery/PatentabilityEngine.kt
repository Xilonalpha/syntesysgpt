package com.synthesis.engine.discovery

import com.synthesis.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Evaluates whether a detected pattern / discovery has realistic patent potential:
 * novelty, non-obviousness, utility, and prior-art risk, then produces a
 * PatentAssessment with a recommended IPC class and a draft summary.
 */
@Singleton
class PatentabilityEngine @Inject constructor() {

    // Unavailable until independently validated evidence and methodology exist.
    fun assess(discovery: Discovery, pattern: PatternDetector.DetectedPattern): PatentAssessment? = null
}
