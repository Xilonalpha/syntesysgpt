"""Check local artifact consistency, not independent NASA authenticity."""
import csv
import hashlib
import json
import math
import sys
from pathlib import Path


def digest(path):
    with Path(path).open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def verify(csv_path, fits_path, tic):
    csv_path, fits_path = Path(csv_path), Path(fits_path)
    m = json.loads(csv_path.with_suffix('.json').read_text())
    if m['tic'] != tic or m['source'] != fits_path.name or m['quality'] != 'QUALITY == 0':
        raise ValueError('Manifest identity or quality mismatch')
    if digest(fits_path) != m['sha256'] or digest(csv_path) != m['csv_sha256']:
        raise ValueError('Artifact hash mismatch')
    if not math.isfinite(m['normalization']) or m['normalization'] <= 0:
        raise ValueError('Invalid normalization')
    with csv_path.open(newline='') as f:
        reader = csv.reader(f)
        if next(reader) != ['bjd', 'flux', 'error']:
            raise ValueError('Invalid header')
        count, previous = 0, -math.inf
        for row in reader:
            t, y, e = map(float, row)
            if not all(map(math.isfinite, (t,y,e))) or e <= 0 or t <= previous:
                raise ValueError('Invalid observation')
            previous = t
            count += 1
    if count < 8 or count != m['points']:
        raise ValueError('Point count mismatch')
    return count

if __name__ == '__main__':
    print('Local artifact consistency verified; points=', verify(sys.argv[1], sys.argv[2], int(sys.argv[3])))
