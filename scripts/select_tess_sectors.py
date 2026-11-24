"""Select the earliest longest contiguous block, capped at three sectors."""
import argparse
import json
from pathlib import Path

def select(sectors, limit=3):
    if type(limit) is not int or not 2 <= limit <= 5:
        raise ValueError("Invalid sector limit")
    if not isinstance(sectors,list) or any(type(s) is not int or s <= 0 for s in sectors):
        raise ValueError("Invalid sectors")
    ordered = sorted(set(sectors))
    blocks = []
    for s in ordered:
        if not blocks or s != blocks[-1][-1]+1:
            blocks.append([])
        blocks[-1].append(s)
    if not blocks:
        raise ValueError("No sectors")
    chosen = max(blocks, key=lambda b:min(len(b),limit))[:limit]
    if len(chosen)<2:
        raise ValueError("No adjacent sector pair; multisector benchmark unavailable")
    return chosen

if __name__ == "__main__":
    p=argparse.ArgumentParser()
    p.add_argument("available",type=Path)
    p.add_argument("output",type=Path)
    a=p.parse_args()
    available=json.loads(a.available.read_text())
    chosen=select(available)
    a.output.write_text(json.dumps({"available":available,"selected":chosen,
        "policy":"earliest longest contiguous block capped at three"},indent=2))
    print(" ".join(map(str,chosen)))
