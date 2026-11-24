package com.synthesis.service

import kotlin.math.*

/** Numerical integration of occulted stellar annuli, quadratic limb darkening.
 * Radii and projected separation are in stellar-radius units. Not a TLS search. */
object PhysicalTransitProfile {
    fun flux(z: Double, k: Double, u1: Double, u2: Double, rings: Int = 512): Double {
        require(z.isFinite() && z >= 0 && k.isFinite() && k in 0.0..1.0)
        require(u1.isFinite() && u2.isFinite() && u1 >= 0 && u1 + u2 <= 1 && u1 + 2*u2 >= 0)
        require(rings in 64..8192)
        if (k == 0.0 || z >= 1 + k) return 1.0
        val total = PI * (1 - u1/3 - u2/6)
        // Split at geometric contact radii to avoid integrating across discontinuities.
        val cuts = listOf(0.0, abs(z-k).coerceIn(0.0,1.0), (z+k).coerceIn(0.0,1.0), 1.0).distinct().sorted()
        var blocked = 0.0
        for (j in 0 until cuts.lastIndex) {
            val a = cuts[j]; val b = cuts[j+1]; val dr = (b-a)/rings
            for (i in 0 until rings) {
                val r = a + (i+0.5)*dr
                val angle = when {
                    z+r <= k -> 2*PI
                    abs(z-r) >= k || z == 0.0 -> 0.0
                    else -> 2*acos(((r*r+z*z-k*k)/(2*r*z)).coerceIn(-1.0,1.0))
                }
                val x = 1-sqrt((1-r*r).coerceAtLeast(0.0))
                blocked += angle*r*(1-u1*x-u2*x*x)*dr
            }
        }
        return (1-blocked/total).coerceIn(0.0,1.0)
    }
}
