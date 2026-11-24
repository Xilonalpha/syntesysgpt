# Scientific integrity patch 02
Based on patch 01. Four Kotlin files modified. Partial audit, not a validated release.
- MAST synchronization: removed synthetic fallback, fabricated timestamps/errors and hard-coded target aliases. Remote path deliberately fails closed pending original calibrated time-series ingestion. Presets disabled.
- Batch: removed unverified campaigns; requires BLS detection; no verified flag or fabricated confidence.
- Photometry import: missing/invalid errors rejected, median normalization, no minimum fake depth/duration or clipped chi-square. FITS frame analysis disabled pending calibrated uncertainties. Unknown derived values use NaN internally; UI/serialization handling still needs audit.
- BLS: validates inputs, preserves input uncertainties, limits search to half baseline, requires three sampled events, fixes phase epoch and actual bin width, removes pseudo-Bayesian confidence. Thresholds are heuristic, not calibrated FAP. Remaining issues include correlated noise, frequency grid resolution, coverage and independent validation.
- No Android build or scientific benchmark completed. Do not publish as validated research software.
