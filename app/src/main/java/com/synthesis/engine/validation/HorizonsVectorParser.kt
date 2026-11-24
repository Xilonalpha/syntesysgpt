package com.synthesis.engine.validation

import kotlin.math.hypot

/** Model ephemerides, not direct instrumental measurements. */
object HorizonsVectorParser {
    data class Vector(val jdTdb: Double, val xAu: Double, val yAu: Double, val zAu: Double) {
        val rangeAu: Double get() = hypot(hypot(xAu,yAu),zAu)
    }
    fun parse(text: String): List<Vector> {
        val start = text.indexOf("$$" + "SOE")
        val end = text.indexOf("$$" + "EOE")
        require(start >= 0 && end > start)
        val header = text.substring(0,start)
        require(Regex("Output units\\s*:\\s*AU-D",RegexOption.IGNORE_CASE).containsMatchIn(header))
        val columns = header.lineSequence().map { it.split(',').map(String::trim) }
            .lastOrNull { it.size >= 5 && it[0] == "JDTDB" && it[2] == "X" && it[3] == "Y" && it[4] == "Z" }
        require(columns != null) { "Unrecognized Horizons vector columns" }
        val rows = text.substring(start+5,end).lineSequence().filter { it.isNotBlank() }.map { line ->
            val fields = line.split(',').map(String::trim)
            require(fields.size >= 5)
            val v = Vector(fields[0].toDouble(),fields[2].toDouble(),fields[3].toDouble(),fields[4].toDouble())
            require(v.jdTdb in 2000000.0..3000000.0 && listOf(v.xAu,v.yAu,v.zAu,v.rangeAu).all { it.isFinite() })
            v
        }.toList()
        require(rows.isNotEmpty() && rows.zipWithNext().all { (a,b) -> a.jdTdb < b.jdTdb })
        return rows
    }
}
