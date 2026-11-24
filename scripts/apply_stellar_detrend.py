"""Apply fixed-scale exploratory filter to a verified photometry CSV."""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
from stellar_detrend import detrend

def apply(source, output):
    source, output = Path(source), Path(output)
    if output.exists() or output.resolve() == source.resolve():
        raise ValueError("Output must be new and distinct")
    data = np.genfromtxt(source, delimiter=",", names=True)
    if data.dtype.names != ("bjd", "flux", "error"):
        raise ValueError("Unexpected photometry schema")
    y, e, baseline, support = detrend(data["bjd"], data["flux"], data["error"], 1.5, .5)
    if support.mean() < .9:
        raise ValueError("Insufficient filter support")
    # Do not mix unsupported raw flux with normalized observations.
    np.savetxt(output, np.column_stack((data["bjd"][support], y[support], e[support])),
               delimiter=",", header="bjd,flux,error", comments="", fmt="%.17g")
    np.savetxt(str(output)+".baseline.csv", np.column_stack((data["bjd"], baseline, support)),
               delimiter=",", header="bjd,baseline,supported", comments="", fmt="%.17g")
    report = {"mode":"exploratory_median", "window_days":1.5, "gap_days":.5,
              "input_points":len(data), "output_points":int(support.sum()),
              "source_sha256":hashlib.sha256(source.read_bytes()).hexdigest(),
              "output_sha256":hashlib.sha256(output.read_bytes()).hexdigest(),
              "baseline_sha256":hashlib.sha256(Path(str(output)+".baseline.csv").read_bytes()).hexdigest(),
              "uncertainty":"conditional scaling only", "global_fap":"NOT_CALIBRATED"}
    Path(str(output)+".json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report))

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("source")
    p.add_argument("output")
    a = p.parse_args()
    apply(a.source, a.output)
