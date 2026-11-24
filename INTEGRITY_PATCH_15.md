# Integrity patch 15
- Catalog mapper rejects absent TIC and invalid TOI; retains TESS/Kepler catalog identity; confirmed rows use actual TIC field instead of hostname. No cross-catalog identity verification is implemented.
- Missing masses stay null; mass-dependent screening requires a supplied mass. Removed fixed K2/confirmed priority and missing Kepler fallback (zero sentinel, not probability). Existing heuristic ranking and temperate screening remain unvalidated.
- Removed static PHL/CHEOPS/Gaia lists and AR target list; these features are temporarily unavailable. Constellation and approximate coordinate code remains.
- Removed unreachable FITS PDF drawing/demo code; export remains blocked.
- Removed unused polling notification methods and clear stale polling counters when paused.
- Corrected selected XILON TLS and vetting claims; remaining report/UI paths require audit.
- Standalone Kotlin catalog check: seven assertions passed using minimal annotation stubs for Gson and javax.inject. No Android build or live catalog verification performed.
