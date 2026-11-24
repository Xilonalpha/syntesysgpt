package com.synthesis.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.synthesis.engine.exoplanet.*
import com.synthesis.viewmodel.exoplanet.*

@Composable
fun ScientificCalculationLab(viewModel: AdvancedDiscoveryViewModel, modes: List<String>) {
    var mode by remember(modes) { mutableStateOf(modes.first()) }
    var raw by remember(mode) { mutableStateOf("") }
    var parameters by remember(mode) { mutableStateOf("") }
    var inputError by remember(mode) { mutableStateOf(false) }
    val state by viewModel.scientificCalculation.collectAsState()
    DisposableEffect(Unit) {
        viewModel.cancelScientificCalculation()
        onDispose { viewModel.cancelScientificCalculation() }
    }
    val hint = when (mode) {
        "Magnetopauză" -> "Câmp ecuatorial (Tesla); densitate protoni (cm⁻³); viteză (km/s)."
        "TTV" -> "Ciclu întreg; epocă (zile); eroare (zile). Un tranzit pe linie, aceeași scară temporală."
        "Orbite" -> "Epoca 1; epoca 2; eroare 1; eroare 2; corelație; perioada minimă; perioada maximă. Zile."
        "Spectru" -> "Lungime de undă (µm); adâncime (ppm); eroare (ppm). Un bin pe linie."
        "Gibbs" -> "Coeficient stoechiometric semnat; activitate adimensională. O specie pe linie."
        else -> "Ingress (ore); egress (ore); eroare ingress; eroare egress; corelație."
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Laborator de calcule preliminare", style = MaterialTheme.typography.titleLarge)
        Text("Date introduse manual; proveniență neverificată. Rezultatele nu confirmă planete, molecule sau viață.")
        modes.forEach { item ->
            OutlinedButton(onClick = {
                viewModel.cancelScientificCalculation()
                mode = item
            }, enabled = item != mode) { Text(item) }
        }
        Text(hint)
        Text("Separator: punct și virgulă; zecimale cu punct.")
        OutlinedTextField(value = raw, onValueChange = {
            raw = it
            inputError = false
            viewModel.cancelScientificCalculation()
        }, label = { Text("Măsurători") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        if (mode == "Spectru" || mode == "Gibbs") {
            OutlinedTextField(value = parameters, onValueChange = {
                parameters = it
                inputError = false
                viewModel.cancelScientificCalculation()
            }, label = { Text(if (mode == "Spectru") "Bandă min; max; continuum min; max (µm)" else "Temperatură (K); ΔG standard (J/mol)") },
                modifier = Modifier.fillMaxWidth())
        }
        if (inputError) Text("Format invalid sau prea multe date.", color = MaterialTheme.colorScheme.error)
        Button(enabled = raw.isNotBlank() && state !is ScientificCalculationState.Running, onClick = {
            try {
                require(raw.length <= 200000 && parameters.length <= 1000)
                fun numbers(line: String, count: Int): List<Double> {
                    val values = line.split(';').map { it.trim().toDouble() }
                    require(values.size == count && values.all { it.isFinite() })
                    return values
                }
                fun rows(count: Int) = raw.lineSequence().filter { it.isNotBlank() }.map { numbers(it, count) }.toList()
                when (mode) {
                    "Magnetopauză" -> numbers(raw, 3).let { viewModel.calculateMagnetopause(it[0],it[1],it[2]) }
                    "TTV" -> viewModel.calculateTimings(rows(3).map {
                        require(it[0] == it[0].toInt().toDouble())
                        TransitTiming(it[0].toInt(), it[1], it[2])
                    })
                    "Orbite" -> numbers(raw, 7).let { viewModel.calculateAliases(it[0],it[1],it[2],it[3],it[4],it[5],it[6]) }
                    "Spectru" -> numbers(parameters, 4).let { p ->
                        viewModel.calculateSpectralContrast(rows(3).map { MeasuredSpectralBin(it[0],it[1],it[2]) }, p[0]..p[1], p[2]..p[3])
                    }
                    "Gibbs" -> numbers(parameters, 2).let { p ->
                        viewModel.calculateReaction(p[0],p[1],rows(2).map { ReactionSpecies(it[0],it[1]) })
                    }
                    else -> numbers(raw, 5).let { viewModel.calculateAsymmetry(it[0],it[1],it[2],it[3],it[4]) }
                }
                inputError = false
            } catch (e: IllegalArgumentException) {
                viewModel.cancelScientificCalculation()
                inputError = true
            }
        }) { Text("Calculează") }
        when (val current = state) {
            ScientificCalculationState.Idle -> Unit
            is ScientificCalculationState.Running -> {
                CircularProgressIndicator()
                OutlinedButton(onClick = { viewModel.cancelScientificCalculation() }) { Text("Anulează") }
            }
            is ScientificCalculationState.Error -> Text(current.message)
            is ScientificCalculationState.Success -> {
                val result = when (val r = current.result) {
                    is ScientificCalculationResult.Magnetopause -> "Presiune: ${r.value.ramPressureNanoPa} nPa; rază: ${r.value.standoffPlanetRadii} Rp; echilibru exterior: ${r.value.balanceAboveSurface}. Nu estimează retenția atmosferică."
                    is ScientificCalculationResult.Timing -> "Perioadă: ${r.value.periodDays} ± ${r.value.periodErrorDays} zile; χ² = ${r.value.chiSquared}; grade libertate: ${r.value.degreesOfFreedom}"
                    is ScientificCalculationResult.Aliases -> "Ipoteze: ${r.value.size}. Primele 50:\n" + r.value.take(50).joinToString("\n") { "${it.cycles}: ${it.periodDays} ± ${it.periodErrorDays} zile" }
                    is ScientificCalculationResult.Contrast -> "Contrast: ${r.value.contrastPpm} ± ${r.value.errorPpm} ppm; sigma locală semnată: ${r.value.signedSigma}"
                    is ScientificCalculationResult.Reaction -> "ΔG: ${r.value.gibbsJPerMol} J/mol; ln Q: ${r.value.logReactionQuotient}"
                    is ScientificCalculationResult.Asymmetry -> "Asimetrie: ${r.value.signedAsymmetry} ± ${r.value.asymmetryError}; diferență sigma: ${r.value.differenceSigma}"
                }
                Text(result)
                Text("Rezultat condițional; ipotezele modelului și proveniența datelor necesită verificare.")
            }
        }
    }
}
