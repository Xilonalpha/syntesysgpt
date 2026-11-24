package com.synthesis
import com.synthesis.engine.normalizer.DataNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test
class UncertaintyNormalizationTest {
    private val normalizer = DataNormalizer()
    @Test fun missingUncertaintyRemainsMissing() {
        for (unit in listOf("°F","hPa","millibar","km"))
            assertEquals(-1f,normalizer.normalizeUncertainty(-1f,unit),0f)
    }
    @Test fun nonfiniteUncertaintyRejected() {
        assertEquals(-1f,normalizer.normalizeUncertainty(Float.NaN,"°C"),0f)
        assertEquals(-1f,normalizer.normalizeUncertainty(Float.POSITIVE_INFINITY,"°C"),0f)
    }
    @Test fun temperatureUncertaintyScalesWithoutOffset() {
        assertEquals(5f,normalizer.normalizeUncertainty(9f,"°F"),0.00001f)
    }
    @Test fun pressureAliasesScale() {
        for (unit in listOf("hPa","millibar","mb"))
            assertEquals(1f,normalizer.normalizeUncertainty(10f,unit),0.00001f)
    }
}
