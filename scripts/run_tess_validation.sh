#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 4 && $# != 7 && $# != 8 ]]; then echo "Usage: CSV EXPECTED_PERIOD FITS TIC [RADIUS MASS TEFF [constrained]]" >&2; exit 2; fi
CSV=$(realpath "$1"); FITS=$(realpath "$3")
cd "$(dirname "$0")/.."
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
if [[ -d "$FITS" ]]; then
    mapfile -d '' SOURCES < <(find "$FITS" -type f -name '*_lc.fits' -print0 | sort -z)
    [[ ${#SOURCES[@]} -ge 2 && ${#SOURCES[@]} -le 3 ]]
    python3 scripts/prepare_tess_multisector.py --tic "$4" --output "$WORK/replay.csv" "${SOURCES[@]}"
    cmp "$CSV" "$WORK/replay.csv"
else
    python3 scripts/verify_tess_manifest.py "$CSV" "$FITS" "$4"
    python3 scripts/prepare_tess.py "$FITS" "$WORK/replay.csv" --tic "$4"
    python3 scripts/compare_tess_replay.py "$CSV" "$WORK/replay.csv"
fi
D=app/src/main/java/com/synthesis
J="$WORK"
kotlinc scripts/discovery_check_stubs/Inject.kt scripts/bls_check_stubs/FitsHeader.kt scripts/TessValidation.kt "$D"/domain/model/exoplanet/{PeriodicTransitModels,BayesianUncertaintyMetrics,TelescopeVisionModels,PhotometricObservation,PhysicalUncertaintyQuantification}.kt "$D"/engine/exoplanet/BayesianUncertaintyEngine.kt "$D"/service/PeriodicTransitAnalysisEngine.kt -include-runtime -d "$J/check.jar"
ANALYSIS="$WORK/replay.csv"
if [[ "${XILON_DETREND:-0}" == 1 ]]; then
    ANALYSIS="$(dirname "$CSV")/observations_detrended.csv"
    python3 scripts/apply_stellar_detrend.py "$WORK/replay.csv" "$ANALYSIS"
fi
java -jar "$J/check.jar" "$ANALYSIS" "$2" "${@:5}"
