import com.synthesis.engine.exoplanet.ExoplanetCandidateFinder
import com.synthesis.data.remote.dto.TessObjectOfInterestDto
import com.synthesis.domain.model.ExoplanetCatalogSource
fun main() {
    val f = ExoplanetCandidateFinder()
    val row = TessObjectOfInterestDto(123.01, 123L, "PC", 2.0, 1.0, 500.0, 2.0, null, null, null, null, null, null, null, null)
    check(f.rankCandidates(listOf(row.copy(tid = null))).isEmpty())
    check(f.rankCandidates(listOf(row.copy(toi = Double.NaN))).isEmpty())
    val c = f.rankCandidates(listOf(row)).single()
    check(c.planetMassEarth == null)
    check(c.catalogSource == ExoplanetCatalogSource.TESS)
    check(f.getPhlHabitableCatalog().isEmpty() && f.getCheopsAndGaiaCatalog().isEmpty())
    check(f.calculateEarthSimilarityIndex(Double.NaN, 288.0) == null)
    check(f.detectAstrophysicalAnomaly(-1.0, 2.0, null, null, false) == null)
    println("7 catalog integrity checks passed")
}
