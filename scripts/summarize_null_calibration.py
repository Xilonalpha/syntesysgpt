import argparse
import csv
import json
from scipy.stats import beta

def summarize(path, expected):
    with open(path, newline="") as f:
        rows = list(csv.DictReader(f))
    if expected < 1 or len(rows) != expected:
        raise ValueError("Incomplete campaign")
    if any(r["kind"] != "null" or float(r["depth_ppm"]) != 0 or
           r["accepted"] not in ("true", "false") or r["recovered"] != "false"
           for r in rows):
        raise ValueError("Invalid null evidence")
    if sorted(int(r["trial"]) for r in rows) != list(range(expected)):
        raise ValueError("Missing or duplicate trial")
    k = sum(r["accepted"] == "true" for r in rows)
    upper = 1.0 if k == expected else float(beta.ppf(.95, k+1, expected-k))
    return {"trials": expected, "false_detections": k, "rate": k/expected,
            "upper_one_sided_95": upper, "below_one_percent": upper < .01,
            "scope": "synthetic null model only"}

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("csv")
    p.add_argument("--expected", type=int, required=True)
    a = p.parse_args()
    print(json.dumps(summarize(a.csv, a.expected), indent=2))
