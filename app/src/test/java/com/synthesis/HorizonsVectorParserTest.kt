package com.synthesis
import com.synthesis.engine.validation.HorizonsVectorParser
import org.junit.Assert.*
import org.junit.Test
class HorizonsVectorParserTest {
    private val sample = "Output units : AU-D\nJDTDB, Calendar Date (TDB), X, Y, Z,\n" +
        "$$" + "SOE\n2459000.5, date, 3, 4, 0,\n" + "$$" + "EOE"
    @Test fun geometricRange() {
        val row = HorizonsVectorParser.parse(sample).single()
        assertEquals(5.0,row.rangeAu,1e-12)
        assertEquals(2459000.5,row.jdTdb,0.0)
    }
    @Test(expected=IllegalArgumentException::class)
    fun rejectsWrongUnits() { HorizonsVectorParser.parse(sample.replace("AU-D","KM-S")) }
    @Test(expected=IllegalArgumentException::class)
    fun rejectsWrongColumns() { HorizonsVectorParser.parse(sample.replace("X, Y, Z","VX, VY, VZ")) }
    @Test(expected=IllegalArgumentException::class)
    fun rejectsNonfinite() { HorizonsVectorParser.parse(sample.replace("3, 4, 0","NaN, 4, 0")) }
}
