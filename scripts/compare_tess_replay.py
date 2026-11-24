"""Require exact reproducibility of local extraction; not proof of NASA origin."""
import json
import sys
from pathlib import Path
from verify_tess_manifest import digest

def compare(original, replay):
    original, replay = Path(original), Path(replay)
    a, b = (json.loads(p.with_suffix('.json').read_text()) for p in (original, replay))
    for key in ('tic', 'sector', 'source', 'sha256', 'csv_sha256', 'points', 'normalization', 'quality'):
        if a[key] != b[key]:
            raise ValueError('Replay manifest mismatch: ' + key)
    if digest(original) != digest(replay) or digest(replay) != b['csv_sha256']:
        raise ValueError('Replay CSV mismatch')

if __name__ == '__main__':
    compare(*sys.argv[1:])
    print('Local extraction replay matched')
