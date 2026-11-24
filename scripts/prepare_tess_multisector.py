"""Combine distinct SPOC sectors without filling gaps or changing original FITS."""
import argparse
import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path
import numpy as np

def combine(sources, output, tic):
    if len(sources) < 2:
        raise ValueError("At least two sectors required")
    output = Path(output)
    if output.exists() or output.with_suffix(".json").exists():
        raise ValueError("Output already exists")
    manifests, arrays = [], []
    with tempfile.TemporaryDirectory() as work:
        for i, source in enumerate(sources):
            csv = Path(work) / f"sector_{i}.csv"
            subprocess.run([sys.executable, str(Path(__file__).with_name("prepare_tess.py")),
                            str(source), str(csv), "--tic", str(tic)], check=True)
            manifest = json.loads(csv.with_suffix(".json").read_text())
            if any(m["sector"] == manifest["sector"] for m in manifests):
                raise ValueError("Duplicate sector")
            manifests.append(manifest)
            arrays.append(np.loadtxt(csv, delimiter=",", skiprows=1, ndmin=2))
    data = np.concatenate(arrays)
    data = data[np.argsort(data[:, 0])]
    if not np.isfinite(data).all() or np.any(np.diff(data[:, 0]) <= 0):
        raise ValueError("Invalid or overlapping timestamps")
    np.savetxt(output, data, delimiter=",", header="bjd,flux,error",
               comments="", fmt="%.17g")
    output.with_suffix(".json").write_text(json.dumps({
        "schema": "tess.multisector.v1", "tic": tic,
        "sectors": sorted(manifests, key=lambda m: m["sector"]),
        "points": len(data), "csv_sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
        "normalization": "independent sector median", "time_system": "BJD_TDB"
    }, indent=2))

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--tic", type=int, required=True)
    p.add_argument("--output", required=True)
    p.add_argument("fits", nargs="+", type=Path)
    a = p.parse_args()
    combine(a.fits, a.output, a.tic)
