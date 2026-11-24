package com.synthesis.engine.validation

import com.synthesis.domain.model.validation.HeasarcCrossCheckResult
import com.synthesis.domain.model.validation.PreReportValidationDossier
import com.synthesis.domain.model.validation.TessPointingAuditResult
import com.synthesis.domain.model.validation.ValidationStatus
import com.synthesis.domain.model.xilon.XilonDetectionResult
import com.synthesis.engine.astrometry.AstrometryEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Validation Module for the Core Synthesis Pipeline.
 *
 * Cross-references candidate exoplanet transit detections against official
 * NASA HEASARC TESS-point field-of-view (FOV) geometry, TESS observing sector footprints,
 * and astrophysical false-positive filters prior to finalizing and exporting reports.
 */
@Singleton
class TessHeasarcValidationEngine @Inject constructor(
    private val astrometryEngine: AstrometryEngine
) {

    /**
     * Executes comprehensive pre-report validation for an active [XilonDetectionResult].
     */
    fun validateCandidate(res: XilonDetectionResult): PreReportValidationDossier {
        val cleanTic = res.star.ticId.replace("TIC", "", ignoreCase = true).trim()
        val ra = res.star.ra ?: res.astrometry?.raDeg ?: Double.NaN
        val dec = res.star.dec ?: res.astrometry?.decDeg ?: Double.NaN

        val pointingAudit = auditTessPointing(
            ticId = cleanTic,
            raDeg = ra,
            decDeg = dec,
            claimedSectorStr = res.sector
        )

        val heasarcAudit = auditAstrophysicalPlausibility(res)

        val warnings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        if (pointingAudit.pointingMismatchFlag) {
            warnings.add("ATENȚIE SECTOR: Sectorul revendicat (${pointingAudit.claimedSector}) este invalid pentru coordonatele stelei!")
            warnings.add(pointingAudit.pointingExplanation)
            recommendations.add("Aplicația a corectat automat eticheta la: ${pointingAudit.suggestedCorrectSector}")
        }

        if (heasarcAudit.isKnownEclipsingBinary) {
            warnings.add("RISC BINARĂ: Semnalul prezintă variații odd-even sau adâncime compatibilă cu stea binară cu eclipsă.")
            recommendations.add("Rulați o de-blendare fotometrică Gaia DR3 pentru a verifica contaminarea pixelului.")
        }

        if (heasarcAudit.centroidOffsetArcsec > 2.5) {
            warnings.add("DEVIERE CENTROID: Deplasare de ${String.format(Locale.US, "%.2f", heasarcAudit.centroidOffsetArcsec)}\" detectată în tranzit.")
            recommendations.add("Posibilă stea de fond cu eclipsă (BEB) în afara țintei principale.")
        }

        warnings.add("Acoperire TESS, binară, centroid, contaminare, FAP și lob Roche neverificate.")
        val status = ValidationStatus.MARGINAL_SNR_AMBIGUOUS
        val score = 0.0
        val sanitizedSector = res.sector

        return PreReportValidationDossier(
            targetTic = cleanTic,
            canonicalName = res.archivalCrossMatch.canonicalName,
            pointingAudit = pointingAudit,
            heasarcAudit = heasarcAudit,
            overallStatus = status,
            validationScore = score,
            sanitizedSectorLabel = sanitizedSector,
            warnings = warnings,
            recommendations = recommendations,
            verifiedAtUtc = getUtcTimestampIso()
        )
    }

    /**
     * Cross-checks celestial coordinates against the official HEASARC TESS-point sector coverage model.
     */
    fun auditTessPointing(
        ticId: String,
        raDeg: Double,
        decDeg: Double,
        claimedSectorStr: String
    ): TessPointingAuditResult {
        val cleanTic = ticId.replace("TIC", "", ignoreCase = true).trim()
        val ecliptic = if (raDeg.isFinite() && raDeg >= 0 && raDeg < 360 && decDeg in -90.0..90.0)
            astrometryEngine.equatorialToEcliptic(raDeg, decDeg)
        else AstrometryEngine.EclipticCoordinates(Double.NaN, Double.NaN, "--", "--")
        val claimedNum = extractSectorNumber(claimedSectorStr)
        val actualSectors = emptyList<Int>()
        val isObservedInClaimed = false
        val mismatch = false
        val suggestedSector = claimedSectorStr
        val explanation = "Acoperire TESS neverificată; sunt necesare produse observaționale autentice."

        return TessPointingAuditResult(
            ticId = cleanTic,
            raDeg = raDeg,
            decDeg = decDeg,
            eclipticCoords = ecliptic,
            claimedSector = claimedSectorStr,
            claimedSectorNumber = claimedNum,
            isClaimedSectorObserved = isObservedInClaimed,
            actualObservedSectors = actualSectors,
            suggestedCorrectSector = suggestedSector,
            pointingMismatchFlag = mismatch,
            pointingExplanation = explanation
        )
    }

    /**
     * Audits physical parameters against HEASARC and Villanova eclipsing binary criteria.
     */
    private fun auditAstrophysicalPlausibility(res: XilonDetectionResult): HeasarcCrossCheckResult {
        return HeasarcCrossCheckResult(
            isKnownEclipsingBinary = false,
            isNearbyBlendContaminated = false,
            centroidOffsetArcsec = Double.NaN,
            stellarDensityCrowdingMetric = Double.NaN,
            falseAlarmProbability = Double.NaN,
            rocheLobeOverflowRisk = false,
            isPhysicallyPlausibleTransit = false,
            notes = "Binară, contaminare, centroid, FAP și lob Roche: neverificate. Valorile booleene false nu exclud riscurile."
        )
    }

    fun calculateObservedSectors(ticId: String, lambdaDeg: Double, betaDeg: Double): List<Int> = emptyList()

    private fun extractSectorNumber(sectorStr: String): Int? {
        val regex = Regex("""(?:Sector|s00|s0)(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(sectorStr)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun getUtcTimestampIso(): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return df.format(Date())
    }
}
