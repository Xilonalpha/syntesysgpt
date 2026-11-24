#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TIC=$1; EXPECTED=$2
mkdir -p evidence
python scripts/download_tess.py --tic "$TIC" --list-sectors --output evidence/availability
python -c 'import json; s=json.load(open("evidence/availability/available_sectors.json")); pairs=[(a,b) for a,b in zip(s,s[1:]) if b==a+1]; assert pairs,"No consecutive sector pair"; print(*pairs[0])' > evidence/selected_sectors.txt
read -ra IDS < evidence/selected_sectors.txt
FITS=()
for sector in "${IDS[@]}"; do
  python scripts/download_tess.py --tic "$TIC" --sector "$sector" --output "evidence/mast/$sector"
  mapfile -t FOUND < <(find "evidence/mast/$sector" -name '*_lc.fits' -type f)
  [[ ${#FOUND[@]} == 1 ]]
  FITS+=("${FOUND[0]}")
done
python scripts/prepare_tess_multisector.py --tic "$TIC" --output evidence/observations.csv "${FITS[@]}"
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
python scripts/prepare_tess_multisector.py --tic "$TIC" --output "$WORK/replay.csv" "${FITS[@]}"
cmp evidence/observations.csv "$WORK/replay.csv"
mapfile -t STELLAR < <(python -c 'import json,math; r=json.load(open("evidence/catalog_reference.json")); assert r["status"]=="PASS"; row=r["catalog_rows"][0]; print("\n".join(str(v) if isinstance(v,(int,float)) and math.isfinite(v) and v>0 else "NaN" for v in (row.get("st_rad"),row.get("st_mass"),row.get("st_teff"))))')
[[ ${#STELLAR[@]} == 3 ]]
ANALYSIS="$WORK/replay.csv"
if [[ "${XILON_DETREND:-0}" == 1 ]]; then
  ANALYSIS=evidence/observations_detrended.csv
  python scripts/apply_stellar_detrend.py "$WORK/replay.csv" "$ANALYSIS"
fi
D=app/src/main/java/com/synthesis
kotlinc scripts/discovery_check_stubs/Inject.kt scripts/bls_check_stubs/FitsHeader.kt scripts/TessValidation.kt "$D"/domain/model/exoplanet/{PeriodicTransitModels,BayesianUncertaintyMetrics,TelescopeVisionModels,PhotometricObservation,PhysicalUncertaintyQuantification}.kt "$D"/engine/exoplanet/BayesianUncertaintyEngine.kt "$D"/service/PeriodicTransitAnalysisEngine.kt -include-runtime -d "$WORK/check.jar"
export XILON_SEARCH_MIN=2 XILON_SEARCH_MAX=15
java -jar "$WORK/check.jar" "$ANALYSIS" "$EXPECTED" "${STELLAR[@]}" constrained
