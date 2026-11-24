# Scientific Integrity Patch 34

Algorithm: BLS-patch34-retry2. When the original BLS search fails, fit a weighted sinusoid across the search grid, reuse working arrays, and retry on a copy only if the sinusoid explains at least 20% of weighted variance. This heuristic is not a calibrated significance threshold. Retry detections are preliminary; original observations are unchanged. The sequential fit is not a simultaneous transit/variability model.

`bash scripts/run_bls_check.sh` completed successfully: 16 regression datasets passed; all four exploratory mixed signals recovered the injected 2.4-day period within 1%; both exploratory binary scenarios rejected. See scripts/bls_patch34_test.log. Exploratory outcomes are reported rather than asserted by the test script. JVM harness uses test stubs; full Android build, phone performance and real TESS validation remain unverified.
