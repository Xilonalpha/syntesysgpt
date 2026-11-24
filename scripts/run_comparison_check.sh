#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/synthesis
J=$(mktemp -d); trap 'rm -rf "$J"' EXIT
kotlinc scripts/discovery_check_stubs/Inject.kt scripts/bls_check_stubs/FitsHeader.kt scripts/ComparisonStabilityCheck.kt "$D"/domain/model/exoplanet/{PeriodicTransitModels,BayesianUncertaintyMetrics,TelescopeVisionModels,PhotometricObservation,PhysicalUncertaintyQuantification}.kt "$D"/engine/exoplanet/BayesianUncertaintyEngine.kt "$D"/service/{PeriodicTransitAnalysisEngine,TelescopeVisionPhotometryEngine,ComparisonStability}.kt -include-runtime -d "$J/check.jar"
java -jar "$J/check.jar"
