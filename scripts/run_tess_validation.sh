#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 4 && $# != 7 && $# != 8 ]]; then echo "Usage: CSV EXPECTED_PERIOD FITS TIC [RADIUS MASS TEFF [constrained]]" >&2; exit 2; fi
CSV=$(realpath "$1"); FITS=$(realpath "$3")
cd "$(dirname "$0")/.."
python3 scripts/verify_tess_manifest.py "$CSV" "$FITS" "$4"
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
python3 scripts/prepare_tess.py "$FITS" "$WORK/replay.csv" --tic "$4"
python3 scripts/compare_tess_replay.py "$CSV" "$WORK/replay.csv"
D=app/src/main/java/com/synthesis
J="$WORK"
kotlinc scripts/discovery_check_stubs/Inject.kt scripts/bls_check_stubs/FitsHeader.kt scripts/TessValidation.kt "$D"/domain/model/exoplanet/{PeriodicTransitModels,BayesianUncertaintyMetrics,TelescopeVisionModels,PhotometricObservation,PhysicalUncertaintyQuantification}.kt "$D"/engine/exoplanet/BayesianUncertaintyEngine.kt "$D"/service/PeriodicTransitAnalysisEngine.kt -include-runtime -d "$J/check.jar"
java -jar "$J/check.jar" "$WORK/replay.csv" "$2" "${@:5}"
