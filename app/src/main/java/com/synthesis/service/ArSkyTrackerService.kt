package com.synthesis.service

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

enum class ArTargetCategory(val labelRo: String) {
    ALL("Toate"),
    ASTROBIOLOGY("🧬 Astrobiologie & Marte"),
    HABITABLE("🌿 Zonă Habitabilă"),
    CLOSE_PROXIMITY("⭐ Apropiate (<50 AL)"),
    SPACE_TELESCOPES("🔭 JWST & TESS"),
    VISIBLE_NOW("👁️ Vizibile Acum")
}

data class ArExoplanetTarget(
    val id: String,
    val name: String,
    val hostStar: String,
    val constellation: String,
    val raDeg: Double,
    val decDeg: Double,
    val distanceLightYears: Double,
    val planetType: String,
    val isHabitableZone: Boolean,
    val transitDurationHours: Double,
    val discoveryMethod: String,
    val category: ArTargetCategory = ArTargetCategory.HABITABLE,
    val missionLabel: String = "NASA Archive",
    val coldScaleLevel: Int = 1,
    val keyBiomarkers: List<String> = emptyList(),
    val abioticCounterExplanation: String = "",
    // Dynamically calculated horizontal coords
    val altitudeDeg: Double = 0.0,
    val azimuthDeg: Double = 0.0,
    val isVisibleAboveHorizon: Boolean = false,
    val screenXFraction: Float = 0.5f,
    val screenYFraction: Float = 0.5f,
    val isInsideCameraFov: Boolean = false,
    val deltaAzDeg: Double = 0.0,
    val deltaAltDeg: Double = 0.0,
    val angularSeparationFromCrosshairDeg: Double = 999.0
)

data class DeviceSkyOrientation(
    val compassAzimuthDeg: Double = 0.0,
    val altitudePitchDeg: Double = 30.0,
    val rollDeg: Double = 0.0,
    val sensorActive: Boolean = false,
    val observerLatitude: Double = 44.4268, // Default Bucharest / Mid-latitude
    val observerLongitude: Double = 26.1025,
    val magneticDeclinationDeg: Double = 0.0,
    val sensorAccuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
    val isTrueNorthCalibrated: Boolean = true
)

data class ArStar(
    val name: String,
    val raDeg: Double,
    val decDeg: Double,
    val magnitude: Double = 2.0
)

data class ArConstellationDefinition(
    val id: String,
    val nameRo: String,
    val nameEn: String,
    val stars: List<ArStar>,
    val connections: List<Pair<Int, Int>>
)

data class ProjectedConstellationLine(
    val constellationId: String,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val isVisible: Boolean
)

data class ProjectedCardinalPoint(
    val label: String,
    val azimuthDeg: Double,
    val screenX: Float,
    val screenY: Float,
    val isInsideFov: Boolean
)

@Singleton
class ArSkyTrackerService @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    // Modern rotation vector sensors (Android 14/15 primary)
    private val rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val geomagneticSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    // Hardware fallback sensors in case rotation vector hardware is missing
    private val accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticFieldSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _deviceOrientation = MutableStateFlow(DeviceSkyOrientation())
    val deviceOrientation: StateFlow<DeviceSkyOrientation> = _deviceOrientation.asStateFlow()

    // 9-element rotation matrices and 3-element orientation angles
    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Fallback sensor reading caches
    private val gravityValues = FloatArray(3)
    private val geomagneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    // Exponential moving average smoothing filters (Jitter attenuation)
    private var smoothedAzimuth: Double = 0.0
    private var smoothedAltitude: Double = 30.0
    private var smoothedRoll: Double = 0.0
    private var isFirstOrientationReading: Boolean = true
    private val smoothingFactor = 0.22 // High responsiveness with zero tremor

    fun startTracking() {
        val manager = sensorManager ?: return
        isFirstOrientationReading = true
        when {
            rotationVectorSensor != null -> {
                manager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
            }
            geomagneticSensor != null -> {
                manager.registerListener(this, geomagneticSensor, SensorManager.SENSOR_DELAY_GAME)
            }
            accelerometerSensor != null && magneticFieldSensor != null -> {
                manager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME)
                manager.registerListener(this, magneticFieldSensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    fun stopTracking() {
        sensorManager?.unregisterListener(this)
        hasGravity = false
        hasGeomagnetic = false
        isFirstOrientationReading = true
    }

    fun setManualOrientation(azimuth: Double, altitude: Double) {
        val normAz = (azimuth % 360.0 + 360.0) % 360.0
        val clampedAlt = altitude.coerceIn(-90.0, 90.0)
        smoothedAzimuth = normAz
        smoothedAltitude = clampedAlt
        _deviceOrientation.value = _deviceOrientation.value.copy(
            compassAzimuthDeg = normAz,
            altitudePitchDeg = clampedAlt,
            sensorActive = false
        )
    }

    fun setObserverCoordinates(lat: Double, lon: Double) {
        val declination = computeMagneticDeclination(lat, lon)
        _deviceOrientation.value = _deviceOrientation.value.copy(
            observerLatitude = lat,
            observerLongitude = lon,
            magneticDeclinationDeg = declination
        )
    }

    private fun computeMagneticDeclination(lat: Double, lon: Double): Double {
        return try {
            val geo = GeomagneticField(
                lat.toFloat(),
                lon.toFloat(),
                0f,
                System.currentTimeMillis()
            )
            geo.declination.toDouble()
        } catch (_: Exception) {
            0.0
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        var hasValidMatrix = false

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR ||
            event.sensor.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
        ) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            hasValidMatrix = true
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravityValues, 0, 3)
            hasGravity = true
            if (hasGeomagnetic) {
                hasValidMatrix = SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, geomagneticValues)
            }
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomagneticValues, 0, 3)
            hasGeomagnetic = true
            if (hasGravity) {
                hasValidMatrix = SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, geomagneticValues)
            }
        }

        if (hasValidMatrix) {
            // Remap coordinate system for camera AR pointing:
            // Device held in portrait looking horizontally or up towards the sky
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remappedMatrix
            )

            SensorManager.getOrientation(remappedMatrix, orientationAngles)

            val rawAzimuthRad = orientationAngles[0]
            val rawPitchRad = orientationAngles[1]
            val rawRollRad = orientationAngles[2]

            var rawAzimuthDeg = Math.toDegrees(rawAzimuthRad.toDouble())
            if (rawAzimuthDeg < 0) rawAzimuthDeg += 360.0

            // Apply True North Astronomical Correction via Geomagnetic Declination
            val currentLat = _deviceOrientation.value.observerLatitude
            val currentLon = _deviceOrientation.value.observerLongitude
            val declinationDeg = computeMagneticDeclination(currentLat, currentLon)
            val trueAzimuthDeg = ((rawAzimuthDeg + declinationDeg) % 360.0 + 360.0) % 360.0

            val rawAltitudePitchDeg = Math.toDegrees(rawPitchRad.toDouble()).coerceIn(-90.0, 90.0)
            val rawRollDeg = Math.toDegrees(rawRollRad.toDouble())

            // Apply Exponential Moving Average Low-Pass Filter
            if (isFirstOrientationReading) {
                smoothedAzimuth = trueAzimuthDeg
                smoothedAltitude = rawAltitudePitchDeg
                smoothedRoll = rawRollDeg
                isFirstOrientationReading = false
            } else {
                smoothedAzimuth = smoothCircularAngle(smoothedAzimuth, trueAzimuthDeg, smoothingFactor)
                smoothedAltitude = smoothedAltitude + (rawAltitudePitchDeg - smoothedAltitude) * smoothingFactor
                smoothedRoll = smoothedRoll + (rawRollDeg - smoothedRoll) * smoothingFactor
            }

            _deviceOrientation.value = _deviceOrientation.value.copy(
                compassAzimuthDeg = smoothedAzimuth,
                altitudePitchDeg = smoothedAltitude,
                rollDeg = smoothedRoll,
                sensorActive = true,
                magneticDeclinationDeg = declinationDeg,
                isTrueNorthCalibrated = true
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        _deviceOrientation.value = _deviceOrientation.value.copy(
            sensorAccuracy = accuracy
        )
    }

    private fun smoothCircularAngle(current: Double, target: Double, factor: Double): Double {
        var diff = (target - current) % 360.0
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0
        return ((current + diff * factor) % 360.0 + 360.0) % 360.0
    }

    fun getFamousTargets(): List<ArExoplanetTarget> = Companion.getFamousTargets()

    fun computeHorizontalCoordinates(
        raDeg: Double,
        decDeg: Double,
        latDeg: Double,
        lonDeg: Double,
        epochMillis: Long = System.currentTimeMillis()
    ): Pair<Double, Double> = Companion.computeHorizontalCoordinates(raDeg, decDeg, latDeg, lonDeg, epochMillis)

    fun computeProjectedTargets(
        device: DeviceSkyOrientation,
        cameraFovHorizontalDeg: Double = 60.0,
        cameraFovVerticalDeg: Double = 45.0,
        filterCategory: ArTargetCategory = ArTargetCategory.ALL,
        epochMillis: Long = System.currentTimeMillis()
    ): List<ArExoplanetTarget> = Companion.computeProjectedTargets(device, cameraFovHorizontalDeg, cameraFovVerticalDeg, filterCategory, epochMillis)

    companion object {

        /**
         * Computes dynamically calculated astronomical ephemeris for Solar System bodies
         * (Mars & Jupiter) at the observation timestamp.
         */
        fun computeSolarSystemPlanetCoords(planetName: String, epochMillis: Long): Pair<Double, Double> {
            val jd = (epochMillis / 86400000.0) + 2440587.5
            val d = jd - 2451545.0

            return when (planetName.lowercase()) {
                "mars" -> {
                    // Mars orbital elements & geocentric projection
                    val lSun = (280.46646 + 0.98564736 * d) % 360.0
                    val mSun = Math.toRadians((357.52911 + 0.98560028 * d) % 360.0)
                    val lambdaSun = lSun + 1.915 * sin(mSun) + 0.020 * sin(2 * mSun)
                    val rSun = 1.00014 - 0.01671 * cos(mSun)

                    val xEarth = rSun * cos(Math.toRadians(lambdaSun))
                    val yEarth = rSun * sin(Math.toRadians(lambdaSun))

                    val lMars = (355.45332 + 0.5240207766 * d) % 360.0
                    val mMars = Math.toRadians((19.38700 + 0.5240207766 * d) % 360.0)
                    val lambdaMars = lMars + 10.691 * sin(mMars) + 0.623 * sin(2 * mMars)
                    val rMars = 1.52368 * (1.0 - 0.0934 * cos(mMars))

                    val xMars = rMars * cos(Math.toRadians(lambdaMars))
                    val yMars = rMars * sin(Math.toRadians(lambdaMars))

                    val dx = xMars - xEarth
                    val dy = yMars - yEarth

                    var geoLongDeg = Math.toDegrees(atan2(dy, dx))
                    if (geoLongDeg < 0) geoLongDeg += 360.0

                    // Convert Ecliptic to Equatorial (Obliquity eps = 23.44 deg)
                    val epsRad = Math.toRadians(23.43929)
                    val longRad = Math.toRadians(geoLongDeg)

                    val sinDec = sin(longRad) * sin(epsRad)
                    val decDeg = Math.toDegrees(asin(sinDec))

                    val yEq = sin(longRad) * cos(epsRad)
                    val xEq = cos(longRad)
                    var raDeg = Math.toDegrees(atan2(yEq, xEq))
                    if (raDeg < 0) raDeg += 360.0

                    Pair(raDeg, decDeg)
                }
                "jupiter" -> {
                    // Jupiter orbital elements
                    val lSun = (280.46646 + 0.98564736 * d) % 360.0
                    val mSun = Math.toRadians((357.52911 + 0.98560028 * d) % 360.0)
                    val lambdaSun = lSun + 1.915 * sin(mSun)
                    val rSun = 1.0

                    val xEarth = rSun * cos(Math.toRadians(lambdaSun))
                    val yEarth = rSun * sin(Math.toRadians(lambdaSun))

                    val lJup = (34.40438 + 0.0830853 * d) % 360.0
                    val mJup = Math.toRadians((19.8950 + 0.0830853 * d) % 360.0)
                    val lambdaJup = lJup + 5.555 * sin(mJup)
                    val rJup = 5.2044 * (1.0 - 0.0484 * cos(mJup))

                    val xJup = rJup * cos(Math.toRadians(lambdaJup))
                    val yJup = rJup * sin(Math.toRadians(lambdaJup))

                    val dx = xJup - xEarth
                    val dy = yJup - yEarth

                    var geoLongDeg = Math.toDegrees(atan2(dy, dx))
                    if (geoLongDeg < 0) geoLongDeg += 360.0

                    val epsRad = Math.toRadians(23.43929)
                    val longRad = Math.toRadians(geoLongDeg)

                    val sinDec = sin(longRad) * sin(epsRad)
                    val decDeg = Math.toDegrees(asin(sinDec))

                    val yEq = sin(longRad) * cos(epsRad)
                    val xEq = cos(longRad)
                    var raDeg = Math.toDegrees(atan2(yEq, xEq))
                    if (raDeg < 0) raDeg += 360.0

                    Pair(raDeg, decDeg)
                }
                else -> Pair(0.0, 0.0)
            }
        }

        // No unverified static astrobiology targets or approximate planetary ephemerides.
        fun getFamousTargets(epochMillis: Long = System.currentTimeMillis()): List<ArExoplanetTarget> = emptyList()

        fun getMajorConstellations(): List<ArConstellationDefinition> {
            return listOf(
                ArConstellationDefinition(
                    id = "cygnus",
                    nameRo = "Lebăda (Zona Misiunilor Kepler)",
                    nameEn = "Cygnus",
                    stars = listOf(
                        ArStar("Deneb", 310.358, 45.280, 1.25),
                        ArStar("Sadr", 305.557, 40.257, 2.23),
                        ArStar("Gienah", 308.293, 33.970, 2.48),
                        ArStar("Delta Cyg", 296.244, 45.131, 2.86),
                        ArStar("Albireo", 292.680, 27.960, 3.05)
                    ),
                    connections = listOf(
                        Pair(0, 1),
                        Pair(1, 4),
                        Pair(1, 2),
                        Pair(1, 3)
                    )
                ),
                ArConstellationDefinition(
                    id = "orion",
                    nameRo = "Orion (Vânătorul)",
                    nameEn = "Orion",
                    stars = listOf(
                        ArStar("Betelgeuse", 88.793, 7.407, 0.50),
                        ArStar("Rigel", 78.634, -8.202, 0.18),
                        ArStar("Bellatrix", 81.283, 6.350, 1.64),
                        ArStar("Saiph", 86.939, -9.669, 2.07),
                        ArStar("Alnitak", 85.190, -1.943, 1.74),
                        ArStar("Alnilam", 84.053, -1.202, 1.69),
                        ArStar("Mintaka", 83.002, -0.299, 2.25)
                    ),
                    connections = listOf(
                        Pair(0, 2),
                        Pair(2, 6),
                        Pair(6, 5),
                        Pair(5, 4),
                        Pair(4, 1),
                        Pair(1, 3),
                        Pair(3, 4),
                        Pair(0, 4)
                    )
                ),
                ArConstellationDefinition(
                    id = "ursa_major",
                    nameRo = "Carul Mare (Ursa Major)",
                    nameEn = "Ursa Major",
                    stars = listOf(
                        ArStar("Dubhe", 165.932, 61.751, 1.79),
                        ArStar("Merak", 165.460, 56.382, 2.37),
                        ArStar("Phecda", 178.458, 53.695, 2.44),
                        ArStar("Megrez", 183.857, 57.033, 3.32),
                        ArStar("Alioth", 193.507, 55.960, 1.76),
                        ArStar("Mizar", 200.981, 54.925, 2.23),
                        ArStar("Alkaid", 206.885, 49.313, 1.85)
                    ),
                    connections = listOf(
                        Pair(0, 1),
                        Pair(1, 2),
                        Pair(2, 3),
                        Pair(3, 0),
                        Pair(3, 4),
                        Pair(4, 5),
                        Pair(5, 6)
                    )
                ),
                ArConstellationDefinition(
                    id = "leo",
                    nameRo = "Leul (Gazda K2-18 b)",
                    nameEn = "Leo",
                    stars = listOf(
                        ArStar("Regulus", 152.093, 11.967, 1.36),
                        ArStar("Denebola", 177.265, 14.572, 2.14),
                        ArStar("Algieba", 154.993, 19.842, 2.01),
                        ArStar("Zosma", 168.527, 20.524, 2.56)
                    ),
                    connections = listOf(
                        Pair(0, 2),
                        Pair(2, 3),
                        Pair(3, 1),
                        Pair(1, 0)
                    )
                ),
                ArConstellationDefinition(
                    id = "aquarius",
                    nameRo = "Vărsătorul (Sistemul TRAPPIST-1)",
                    nameEn = "Aquarius",
                    stars = listOf(
                        ArStar("Sadalmelik", 331.442, -0.320, 2.95),
                        ArStar("Sadalsuud", 322.885, -5.571, 2.90),
                        ArStar("Sadachbia", 335.412, -1.388, 3.86),
                        ArStar("Skat", 343.660, -15.820, 3.27)
                    ),
                    connections = listOf(
                        Pair(1, 0),
                        Pair(0, 2),
                        Pair(2, 3)
                    )
                )
            )
        }

        fun computeHorizontalCoordinates(
            raDeg: Double,
            decDeg: Double,
            latDeg: Double,
            lonDeg: Double,
            epochMillis: Long = System.currentTimeMillis()
        ): Pair<Double, Double> {
            val jd = (epochMillis / 86400000.0) + 2440587.5
            val d = jd - 2451545.0

            var gmst = 280.46061837 + 360.98564736629 * d
            gmst = (gmst % 360.0 + 360.0) % 360.0

            var lstDeg = gmst + lonDeg
            lstDeg = (lstDeg % 360.0 + 360.0) % 360.0

            var haDeg = lstDeg - raDeg
            haDeg = (haDeg % 360.0 + 360.0) % 360.0

            val haRad = Math.toRadians(haDeg)
            val decRad = Math.toRadians(decDeg)
            val latRad = Math.toRadians(latDeg)

            val sinAlt = sin(decRad) * sin(latRad) + cos(decRad) * cos(latRad) * cos(haRad)
            val altRad = asin(sinAlt.coerceIn(-1.0, 1.0))
            val altDeg = Math.toDegrees(altRad)

            val cosAz = (sin(decRad) - sin(latRad) * sinAlt) / (cos(latRad) * cos(altRad)).coerceAtLeast(1e-6)
            var azRad = acos(cosAz.coerceIn(-1.0, 1.0))
            var azDeg = Math.toDegrees(azRad)

            if (sin(haRad) > 0) {
                azDeg = 360.0 - azDeg
            }

            return Pair(altDeg, azDeg)
        }

        fun computeProjectedTargets(
            device: DeviceSkyOrientation,
            cameraFovHorizontalDeg: Double = 60.0,
            cameraFovVerticalDeg: Double = 45.0,
            filterCategory: ArTargetCategory = ArTargetCategory.ALL,
            epochMillis: Long = System.currentTimeMillis()
        ): List<ArExoplanetTarget> {
            val allTargets = getFamousTargets(epochMillis)
            val filtered = when (filterCategory) {
                ArTargetCategory.ALL -> allTargets
                ArTargetCategory.ASTROBIOLOGY -> allTargets.filter { it.category == ArTargetCategory.ASTROBIOLOGY }
                ArTargetCategory.HABITABLE -> allTargets.filter { it.isHabitableZone }
                ArTargetCategory.CLOSE_PROXIMITY -> allTargets.filter { it.distanceLightYears < 50.0 }
                ArTargetCategory.SPACE_TELESCOPES -> allTargets.filter { it.category == ArTargetCategory.SPACE_TELESCOPES }
                ArTargetCategory.VISIBLE_NOW -> allTargets // filtered later by alt > 0
            }

            return filtered.map { target ->
                val (alt, az) = computeHorizontalCoordinates(
                    target.raDeg,
                    target.decDeg,
                    device.observerLatitude,
                    device.observerLongitude,
                    epochMillis
                )

                var deltaAz = az - device.compassAzimuthDeg
                while (deltaAz > 180.0) deltaAz -= 360.0
                while (deltaAz < -180.0) deltaAz += 360.0

                val deltaAlt = alt - device.altitudePitchDeg

                val halfFovH = cameraFovHorizontalDeg / 2.0
                val halfFovV = cameraFovVerticalDeg / 2.0

                val screenX = 0.5f + (deltaAz / halfFovH).toFloat() * 0.5f
                val screenY = 0.5f - (deltaAlt / halfFovV).toFloat() * 0.5f

                val isInside = deltaAz in -halfFovH..halfFovH && deltaAlt in -halfFovV..halfFovV && alt > -5.0

                val separationDeg = sqrt(deltaAz * deltaAz + deltaAlt * deltaAlt)

                target.copy(
                    altitudeDeg = (alt * 10.0).roundToLong() / 10.0,
                    azimuthDeg = (az * 10.0).roundToLong() / 10.0,
                    isVisibleAboveHorizon = alt > 0.0,
                    screenXFraction = screenX.coerceIn(-0.2f, 1.2f),
                    screenYFraction = screenY.coerceIn(-0.2f, 1.2f),
                    isInsideCameraFov = isInside,
                    deltaAzDeg = (deltaAz * 10.0).roundToLong() / 10.0,
                    deltaAltDeg = (deltaAlt * 10.0).roundToLong() / 10.0,
                    angularSeparationFromCrosshairDeg = (separationDeg * 10.0).roundToLong() / 10.0
                )
            }.let { list ->
                if (filterCategory == ArTargetCategory.VISIBLE_NOW) {
                    list.filter { it.isVisibleAboveHorizon }
                } else {
                    list
                }
            }
        }

        fun computeProjectedConstellations(
            device: DeviceSkyOrientation,
            cameraFovHorizontalDeg: Double = 60.0,
            cameraFovVerticalDeg: Double = 45.0,
            epochMillis: Long = System.currentTimeMillis()
        ): List<ProjectedConstellationLine> {
            val constellations = getMajorConstellations()
            val result = mutableListOf<ProjectedConstellationLine>()

            val halfFovH = cameraFovHorizontalDeg / 2.0
            val halfFovV = cameraFovVerticalDeg / 2.0

            for (const in constellations) {
                val projectedStars = const.stars.map { star ->
                    val (alt, az) = computeHorizontalCoordinates(
                        star.raDeg,
                        star.decDeg,
                        device.observerLatitude,
                        device.observerLongitude,
                        epochMillis
                    )

                    var deltaAz = az - device.compassAzimuthDeg
                    while (deltaAz > 180.0) deltaAz -= 360.0
                    while (deltaAz < -180.0) deltaAz += 360.0
                    val deltaAlt = alt - device.altitudePitchDeg

                    val screenX = 0.5f + (deltaAz / halfFovH).toFloat() * 0.5f
                    val screenY = 0.5f - (deltaAlt / halfFovV).toFloat() * 0.5f
                    val isNear = deltaAz in -cameraFovHorizontalDeg..cameraFovHorizontalDeg &&
                                 deltaAlt in -cameraFovVerticalDeg..cameraFovVerticalDeg &&
                                 alt > -10.0

                    Triple(screenX, screenY, isNear)
                }

                for ((idx1, idx2) in const.connections) {
                    if (idx1 in projectedStars.indices && idx2 in projectedStars.indices) {
                        val s1 = projectedStars[idx1]
                        val s2 = projectedStars[idx2]

                        if (s1.third || s2.third) {
                            result.add(
                                ProjectedConstellationLine(
                                    constellationId = const.id,
                                    startX = s1.first,
                                    startY = s1.second,
                                    endX = s2.first,
                                    endY = s2.second,
                                    isVisible = s1.third && s2.third
                                )
                            )
                        }
                    }
                }
            }
            return result
        }

        fun computeProjectedCardinalPoints(
            device: DeviceSkyOrientation,
            cameraFovHorizontalDeg: Double = 60.0,
            cameraFovVerticalDeg: Double = 45.0
        ): List<ProjectedCardinalPoint> {
            val cardinals = listOf(
                Pair("NORD", 0.0),
                Pair("NE", 45.0),
                Pair("EST", 90.0),
                Pair("SE", 135.0),
                Pair("SUD", 180.0),
                Pair("SV", 225.0),
                Pair("VEST", 270.0),
                Pair("NV", 315.0)
            )

            val halfFovH = cameraFovHorizontalDeg / 2.0
            val halfFovV = cameraFovVerticalDeg / 2.0

            return cardinals.map { (label, az) ->
                var deltaAz = az - device.compassAzimuthDeg
                while (deltaAz > 180.0) deltaAz -= 360.0
                while (deltaAz < -180.0) deltaAz += 360.0

                val deltaAlt = 0.0 - device.altitudePitchDeg

                val screenX = 0.5f + (deltaAz / halfFovH).toFloat() * 0.5f
                val screenY = 0.5f - (deltaAlt / halfFovV).toFloat() * 0.5f

                val isInside = deltaAz in -halfFovH..halfFovH && deltaAlt in -halfFovV..halfFovV

                ProjectedCardinalPoint(
                    label = label,
                    azimuthDeg = az,
                    screenX = screenX,
                    screenY = screenY,
                    isInsideFov = isInside
                )
            }
        }
    }
}
