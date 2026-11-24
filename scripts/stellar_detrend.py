"""Exploratory detrending, not a validated transit-preserving filter."""
import numpy as np

def robust_local(x, y, e):
    base = np.maximum(0., 1-np.abs(x)**3)**3 * (e.min()/e)**2
    a = np.column_stack((np.ones(len(x)), x, x*x, x*x*x))
    w = base.copy()
    for _ in range(8):
        root = np.sqrt(w)
        b, _, rank, _ = np.linalg.lstsq(a*root[:,None], y*root, rcond=None)
        if rank != 4:
            raise ValueError("Degenerate local regression")
        residual = y-a@b
        scale = max(1.4826*np.median(abs(residual-np.median(residual))), 1e-12)
        w = base*np.where(residual < 0,
            np.minimum(1., (1.5*scale/np.maximum(abs(residual),1e-12))**2), 1.)
    return float(b[0])

def detrend(time, flux, error, window_days=1.5, gap_days=0.5):
    t, y, e = (np.asarray(v, dtype=float) for v in (time, flux, error))
    if any(v.ndim != 1 for v in (t, y, e)) or not (len(t) == len(y) == len(e)):
        raise ValueError("Aligned one-dimensional arrays required")
    if len(t) < 8 or not all(np.isfinite(v).all() for v in (t, y, e)):
        raise ValueError("Invalid samples")
    if np.any(np.diff(t) <= 0) or np.any(e <= 0):
        raise ValueError("Invalid timestamps or uncertainties")
    if not np.isfinite(window_days) or not np.isfinite(gap_days) or not 0 < gap_days < window_days:
        raise ValueError("Invalid filter scales")
    baseline = np.ones(len(t))
    supported = np.zeros(len(t), dtype=bool)
    edges = np.r_[0, np.flatnonzero(np.diff(t) > gap_days) + 1, len(t)]
    for start, end in zip(edges[:-1], edges[1:]):
        ts, ys, es = t[start:end], y[start:end], e[start:end]
        left = np.searchsorted(ts, ts - window_days / 2)
        right = np.searchsorted(ts, ts + window_days / 2, side="right")
        for i, (lo, hi) in enumerate(zip(left, right)):
            if hi - lo < 15 or ts[hi-1] - ts[lo] < window_days / 2:
                continue
            value = robust_local((ts[lo:hi]-ts[i])/(window_days/2), ys[lo:hi], es[lo:hi])
            if not np.isfinite(value) or value <= 0:
                raise ValueError("Nonpositive baseline")
            baseline[start+i] = value
            supported[start+i] = True
    # Unsupported samples are unchanged; callers must inspect support before analysis.
    # Error scaling is conditional on the estimated baseline, not full propagation.
    return y / baseline, e / baseline, baseline, supported
