import com.synthesis.engine.astrometry.AstrometryEngine
fun main(){ val a=AstrometryEngine(); check(a.unixMsToJdUtc(1704067200000L)==2460310.5); check(a.observedBjdToBtjd(2460310.5)==3310.5); check(a.generateDossier(99.0,-67.0,"123","test").bjdTdbCurrent==null); check(runCatching{a.equatorialToGalactic(0.0,91.0)}.isFailure); println("4 checks passed") }
