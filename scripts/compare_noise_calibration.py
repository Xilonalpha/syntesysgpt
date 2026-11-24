import csv
import json
import math
import sys
from collections import defaultdict

def summarize(path, expected):
    groups = defaultdict(list)
    with open(path, newline="") as stream:
        for row in csv.DictReader(stream):
            groups[float(row["depth_ppm"])].append(row)
    if set(groups) != {0., 300., 600., 1500., 5000.}:
        raise ValueError("Missing depth groups")
    output = []
    for depth, rows in sorted(groups.items()):
        if len(rows) != expected or {int(r["trial"]) for r in rows} != set(range(expected)):
            raise ValueError("Incomplete or duplicate trials")
        raw = adjusted = missing = 0
        for row in rows:
            accepted = row["accepted"] == "true"
            recovered = row["recovered"] == "true"
            metric = row["adjusted_snr"]
            snr = float(metric) if metric else math.nan
            valid = math.isfinite(snr) and snr >= 0
            missing += not valid
            hit = accepted if depth == 0 else recovered
            raw += hit
            adjusted += hit and valid and snr >= 7
        output.append(dict(depth_ppm=depth, trials=expected,
                           metric="false_alarm" if depth == 0 else "period_recovery",
                           original_count=raw, adjusted_count=adjusted,
                           original_rate=raw/expected, adjusted_rate=adjusted/expected,
                           missing_diagnostic=missing))
    return {"scope": "synthetic paired comparison; exploratory fixed SNR gate, not validated FAP",
            "adjusted_snr_threshold": 7, "groups": output}

if __name__ == "__main__":
    print(json.dumps(summarize(sys.argv[1], int(sys.argv[2])), indent=2))
