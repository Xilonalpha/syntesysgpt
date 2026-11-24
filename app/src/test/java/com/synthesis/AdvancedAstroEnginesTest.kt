package com.synthesis

import com.synthesis.domain.model.exoplanet.AtmosphericMolecule
import com.synthesis.domain.model.exoplanet.ClimateRegime
import com.synthesis.engine.exoplanet.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class AdvancedAstroEnginesTest {

    private val bioEngine = BiosignatureTechnosignatureEngine()
    private val deblendEngine = DilutionDeBlenderEngine()
    private val stitcherEngine = MultiSectorHarmonicStitcherEngine()
    private val climateEngine = EnergyBalanceClimateEngine()
    private val radarEngine = TransitSkyRadarEngine()

    @Test(expected = UnsupportedOperationException::class)
    fun testBiosignatureAtmosphericDisequilibrium() {
        println("=== TEST 1: BIOSIGNATURE ATMOSPHERIC CHEMICAL DISEQUILIBRIUM ===")
        val molecules = listOf(
            AtmosphericMolecule("CH4", "Methane", 15.0, 4.2, isBiosignatureCandidate = true, isTechnosignatureCandidate = false),
            AtmosphericMolecule("O2", "Molecular Oxygen", 200000.0, 5.1, isBiosignatureCandidate = true, isTechnosignatureCandidate = false),
            AtmosphericMolecule("DMS", "Dimethyl Sulfide", 18.0, 3.2, isBiosignatureCandidate = true, isTechnosignatureCandidate = false),
            AtmosphericMolecule("CO2", "Carbon Dioxide", 400.0, 7.0, isBiosignatureCandidate = false, isTechnosignatureCandidate = false)
        )

        val bio = bioEngine.evaluateBiosignatures(
            planetName = "TRAPPIST-1 e Bio-Candidate",
            hostStar = "TRAPPIST-1",
            equilibriumTempK = 250.0,
            molecules = molecules
        )
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testTechnosignatureMegastructureAsymmetry() {
        println("=== TEST 2: TECHNO-SIGNATURE & LIGHT CURVE ASYMMETRY ===")
        val tech = bioEngine.evaluateTechnosignatures(
            candidateName = "Boyajian Megastructure Benchmark",
            hostStar = "KIC 8462852",
            ingressDurationHours = 18.0,
            egressDurationHours = 6.0,
            depthVariancePpm = 20000.0,
            cfcPollutionSigma = 3.8
        )
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testMagnitudeOnlyDilutionIsRejected() {
        deblendEngine.deblendCandidate("Synthetic fixture", "300015239", "FP",
            340.0, 10.45, 10.82, 4.2, 0.88)
    }

    @Test
    fun testMeasuredApertureDilution() {
        val estimate = deblendEngine.deblendMeasured(1000.0, 10.0, 100.0, 2.0, 50.0, 3.0)
        assertEquals(1.5, estimate.correctionFactor, 1e-10)
        assertEquals(1500.0, estimate.depthPpm, 1e-8)
        assertEquals(35.0, estimate.depthErrorPpm, 1e-8)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testHarmonicStitcherMultiYearBridge() {
        println("=== TEST 4: MULTI-SECTOR HARMONIC STITCHER ===")
        // Sector 14 (BTJD 1683.15) to Sector 44 (BTJD 2496.15) -> Delta T = 813.0 days
        val stitch = stitcherEngine.stitchHarmonics(
            candidateName = "TOI-4633.02",
            ticId = "100100827",
            epoch1Btjd = 1683.15,
            sector1 = "s0014",
            epoch2Btjd = 2496.15,
            sector2 = "s0044",
            stellarMassSun = 1.02,
            stellarRadiusSun = 1.05,
            stellarTeffK = 5790.0,
            monitoredGaps = listOf(Pair(1900.0, 1927.0), Pair(2150.0, 2177.0))
        )
    }

    @Test
    fun testBudykoSellersClimateSimulator() {
        println("=== TEST 5: 1D BUDYKO-SELLERS CLIMATE SIMULATOR ===")
        // 1. Earth Baseline (S = 1.0, CO2 = 400 ppm, P = 1 bar)
        val earthClimate = climateEngine.simulateClimate(
            planetName = "Earth Baseline",
            stellarInsolationSolar = 1.0,
            co2LevelBar = 0.0004,
            surfacePressureBar = 1.0
        )

        println("Earth Analog Mean Temp: ${earthClimate.meanSurfaceTempC}°C")
        println("Earth Analog Polar Ice Line: ${earthClimate.polarIceLatitudeDeg}°")
        println("Earth Liquid Ocean: ${earthClimate.liquidOceanCoveragePercent}%")
        println("Earth Regime: ${earthClimate.climateRegime}")

        assertTrue("Earth analog mean temp should be temperate (5 to 25 °C)", earthClimate.meanSurfaceTempC in 5.0..25.0)
        assertTrue("Earth analog ice line should be temperate (38 to 80 °)", earthClimate.polarIceLatitudeDeg in 38.0..80.0)
        assertEquals("Earth analog regime should be TEMPERATE_EARTH_LIKE", ClimateRegime.TEMPERATE_EARTH_LIKE, earthClimate.climateRegime)

        // 2. Runaway Greenhouse (S = 1.4)
        val runaway = climateEngine.simulateClimate("Venus Analog", 1.45, 0.05, 10.0)
        println("Runaway Regime: ${runaway.climateRegime}, Temp: ${runaway.meanSurfaceTempC}°C")
        assertEquals(ClimateRegime.RUNAWAY_GREENHOUSE, runaway.climateRegime)

        // 3. Snowball Glaciation (S = 0.65)
        val snowball = climateEngine.simulateClimate("Frozen Ice World", 0.65, 0.0001, 0.5)
        println("Snowball Regime: ${snowball.climateRegime}, Temp: ${snowball.meanSurfaceTempC}°C")
        assertEquals(ClimateRegime.SNOWBALL_GLACIATED, snowball.climateRegime)
    }

    @Test
    fun testTransitSkyRadarCountdown() {
        println("=== TEST 6: TRANSIT SKY RADAR COUNTDOWN ===")
        val radar = radarEngine.computeNextTransit(
            candidateName = "TOI-700 e",
            ticId = "150428135",
            constellation = "Dorado",
            rightAscension = "06h 28m 23s",
            declination = "-55° 34′ 43″",
            apparentVisualMag = 13.1,
            periodDays = 27.81,
            transitDurationHours = 2.4,
            epochBtjd = 1500.24,
            transitDepthPpm = 860.0,
            currentBtjd = 3200.0
        )

        println("Next Transit Epoch: BTJD ${radar.nextTransitEpochBtjd}")
        println("Hours Until Ingress: ${radar.hoursUntilIngress} h")
        println("Feasibility: ${radar.amateurTelescopeFeasibility}")

        assertTrue("Next epoch must be >= current epoch", radar.nextTransitEpochBtjd >= 3200.0)
        assertTrue("Hours until ingress must be non-negative", radar.hoursUntilIngress >= 0.0)
        assertNotNull(radar.amateurTelescopeFeasibility)
    }

    @Test
    fun testDynamicTransitAlertCalculations() {
        println("=== TEST 7: DYNAMIC TRANSIT ALERT MESSAGE CALCULATIONS ===")
        val radar = radarEngine.computeNextTransit(
            candidateName = "TOI-1895.01",
            ticId = "384159646",
            constellation = "Cygnus",
            rightAscension = "19h 42m 12s",
            declination = "+41° 18′ 29″",
            apparentVisualMag = 9.75,
            periodDays = 194.5,
            transitDurationHours = 14.8,
            epochBtjd = 2390.412,
            transitDepthPpm = 4850.0,
            currentBtjd = 3200.0
        )

        // Verify dynamic values
        assertTrue("Hours until ingress must be dynamically calculated", radar.hoursUntilIngress > 0.0)
        assertEquals("Cygnus", radar.constellation)
        assertEquals("19h 42m 12s", radar.rightAscension)
        assertEquals("+41° 18′ 29″", radar.declination)
    }
}
