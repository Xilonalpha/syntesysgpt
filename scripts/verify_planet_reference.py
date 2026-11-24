"""Validate benchmark references against NASA Exoplanet Archive PSCompPars."""
import argparse
import json
import math
import re
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import urlopen

ENDPOINT = "https://exoplanetarchive.ipac.caltech.edu/TAP/sync"

def validate(rows, name, tic, period):
    if len(rows) != 1:
        raise ValueError("Expected exactly one catalog planet")
    row = rows[0]
    if row.get("pl_name", "").casefold() != name.casefold():
        raise ValueError("Planet name mismatch")
    match = re.fullmatch(r"TIC\s+(\d+)", str(row.get("tic_id", "")), re.I)
    if not match or int(match[1]) != tic:
        raise ValueError("TIC mismatch or missing catalog identity")
    reference = float(row["pl_orbper"])
    if not math.isfinite(reference) or reference <= 0 or not math.isfinite(period) or period <= 0:
        raise ValueError("Invalid period")
    if abs(period / reference - 1) > .005:
        raise ValueError("Configured period differs from catalog by more than 0.5%")
    return row

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--name", required=True)
    p.add_argument("--tic", type=int, required=True)
    p.add_argument("--period", type=float, required=True)
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9 .+-]+", a.name):
        raise ValueError("Invalid catalog name")
    query = ("select pl_name,hostname,tic_id,pl_orbper,"
             "pl_orbpererr1,pl_orbpererr2,st_rad,st_mass,st_teff,st_raderr1,st_raderr2,st_masserr1,st_masserr2,st_tefferr1,st_tefferr2 "
             "from pscomppars where pl_name = '" + a.name + "'")
    url = ENDPOINT + "?" + urlencode({"query": query, "format": "json"})
    with urlopen(url, timeout=60) as response:
        rows = json.load(response)
    evidence = {"source": url, "configured": {"name": a.name, "tic": a.tic, "period": a.period},
                "catalog_rows": rows}
    try:
        validate(rows, a.name, a.tic, a.period)
    except (ValueError, TypeError, KeyError) as exc:
        evidence.update(status="FAIL", reason=str(exc))
        a.output.write_text(json.dumps(evidence, indent=2))
        raise
    evidence["status"] = "PASS"
    a.output.write_text(json.dumps(evidence, indent=2))

if __name__ == "__main__":
    main()
