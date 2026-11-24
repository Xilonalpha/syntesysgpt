# Patch 41 — physical transit profile
Independent Kotlin numerical model of an opaque circular planet occulting a circular star with quadratic limb darkening. Integrates annular occultation angles with midpoint quadrature, splitting at contact radii. Inputs are projected separation and planet radius in stellar radii, and physical limb-darkening coefficients. Normalizes by analytic unobscured stellar flux. No synthetic observations are exposed to the application.

Run `scripts/run_physical_transit_check.sh` for ten JVM checks, including analytic uniform disk cases and one convergence check. See `scripts/physical_transit_patch41.log`.

Not yet connected to BLS or Android UI. Not a complete TLS implementation: no orbital search, exposure integration, parameter estimation, uncertainties, or independent reference validation. No Android build or real TESS validation performed. Next: weighted fitting at fixed candidate ephemeris, exposure integration and independent reference comparisons.
