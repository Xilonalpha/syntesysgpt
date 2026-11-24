#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=${1:-evidence/multisignal}
mkdir -p "$OUT"
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
D=app/src/main/java/com/synthesis
kotlinc scripts/discovery_check_stubs/Inject.kt scripts/bls_check_stubs/FitsHeader.kt scripts/TessMultiValidation.kt "$D"/domain/model/exoplanet/{PeriodicTransitModels,BayesianUncertaintyMetrics,TelescopeVisionModels,PhotometricObservation,PhysicalUncertaintyQuantification}.kt "$D"/engine/exoplanet/BayesianUncertaintyEngine.kt "$D"/service/PeriodicTransitAnalysisEngine.kt -include-runtime -d "$WORK/check.jar"
failed=0
for seed in 11801 11802 11803; do
  python3 scripts/generate_multisignal_benchmark.py "$OUT/$seed.csv" --seed "$seed"
  if ! java -jar "$WORK/check.jar" "$OUT/$seed.csv" 1.0 5.0 2.13,3.47 2>&1 | tee "$OUT/$seed.log"; then
    failed=1
  fi
done
exit "$failed"
