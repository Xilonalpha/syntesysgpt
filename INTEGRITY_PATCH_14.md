# Integrity patch 14
- FITS summary uses empty metadata rather than a fabricated observation; missing optical and coordinate values display --. Preliminary signals are not confirmed transits.
- GPS transit alerts fail closed until ephemeris and local visibility are validated; ViewModel no longer claims a calculated window on failure.
- TAN WCS requires finite reference coordinates, explicit scale or complete nonsingular CD matrix, and supported projection. Parser rejects unsupported PC/PV/distortion and non-degree units. FITS pixel coordinates are one-based; no full WCS standards compliance claimed.
- Six standalone Kotlin checks passed using the exact extracted FitsWcsInfo class (reference pixel, missing scale, unsupported projection, incomplete matrix, singular matrix, NaN). Android compilation and parser integration tests not run.
- Existing PDF generator remains disabled; its private legacy drawing code still contains fabricated defaults. Catalog identity, TLS, other UI claims and background notifications remain to audit.
