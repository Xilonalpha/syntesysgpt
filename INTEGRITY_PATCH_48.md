# Patch 48 — Integrated JVM experiment
Added scripts/IntegratedTransitCheck.kt and runner. Initial noiseless injection failed BLS (zero MAD is rejected by existing engine); initial log retained. A deterministic perturbation of amplitude 200 ppm was added to the injection; this is a different experiment, not a production fix. The flat control remains noiseless. Physical model used to generate and fit the injection, so this is not independent validation. No production algorithm changed. No Android build or NASA data validation.
Run: bash scripts/run_integrated_transit_check.sh
