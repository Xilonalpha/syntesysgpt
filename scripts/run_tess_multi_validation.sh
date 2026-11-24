#!/usr/bin/env bash
set -euo pipefail
if [[ $# -lt 6 ]]; then echo "Usage: CSV TIC MIN MAX EXPECTED FITS..." >&2; exit 2; fi
CSV=$(realpath "$1"); TIC=$2; LO=$3; HI=$4; EXPECTED=$5
shift 5
FITS=()
for f in "$@"; do FITS+=("$(realpath "$f")"); done
cd "$(dirname "$0")/.."
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
if [[ ${#FITS[@]} == 1 ]]; then
  python3 scripts/prepare_tess.py "${FITS[0]}" "$WORK/replay.csv" --tic "$TIC"
else
  python3 scripts/prepare_tess_multisector.py --tic "$TIC" --output "$WORK/replay.csv" "${FITS[@]}"
fi
cmp "$CSV" "$WORK/replay.csv"
D=app/src/main/java/com/synthesis
kotlinc scripts/discovery_check_stubs/Inject.kt scripts/bls_check_stubs/FitsHeader.kt scripts/TessMultiValidation.kt "$D"/domain/model/exoplanet/{PeriodicTransitModels,BayesianUncertaintyMetrics,TelescopeVisionModels,PhotometricObservation,PhysicalUncertaintyQuantification}.kt "$D"/engine/exoplanet/BayesianUncertaintyEngine.kt "$D"/engine/exoplanet/CorrelatedNoisePhysics.kt "$D"/service/PeriodicTransitAnalysisEngine.kt -include-runtime -d "$WORK/check.jar"
java -jar "$WORK/check.jar" "$WORK/replay.csv" "$LO" "$HI" "$EXPECTED"
