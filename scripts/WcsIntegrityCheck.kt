fun main() {
    val w = FitsWcsInfo(crval1=68.0, crval2=29.0, crpix1=100.0, crpix2=100.0, cdelt1=-0.001, cdelt2=0.001, ctype1="RA---TAN", ctype2="DEC--TAN")
    val center = w.pixelToRaDec(100.0,100.0)!!
    check(kotlin.math.abs(center.first-68.0)<1e-9 && kotlin.math.abs(center.second-29.0)<1e-9)
    check(w.copy(cdelt1=null).pixelToRaDec(100.0,100.0)==null)
    check(w.copy(ctype1="RA---SIN").pixelToRaDec(100.0,100.0)==null)
    check(w.copy(cd11=1.0).pixelToRaDec(100.0,100.0)==null)
    check(w.copy(cd11=1.0,cd12=1.0,cd21=1.0,cd22=1.0).pixelToRaDec(100.0,100.0)==null)
    check(w.pixelToRaDec(Double.NaN,100.0)==null)
    println("6 checks passed")
}
