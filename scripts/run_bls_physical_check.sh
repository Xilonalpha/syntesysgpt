#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/synthesis
J=$(mktemp -d); trap 'rm -rf "$J"' EXIT
kotlinc "$D"/service/{PhysicalTransitProfile,PhysicalTransitFit,TransitGeometryFit,BlsPhysicalRefinement}.kt "$D"/domain/model/exoplanet/{PeriodicTransitModels,BayesianUncertaintyMetrics}.kt scripts/BlsPhysicalCheck.kt -include-runtime -d "$J/check.jar"
java -jar "$J/check.jar"
