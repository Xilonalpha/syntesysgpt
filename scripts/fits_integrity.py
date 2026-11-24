"""Verify original FITS records, including CHECKSUM without DATASUM."""
from pathlib import Path
import numpy as np

def ones_sum(data):
    if len(data) % 4:
        raise ValueError("Unaligned FITS records")
    total = 0
    words = np.frombuffer(data, dtype=">u4")
    for start in range(0, len(words), 65536):
        total += int(words[start:start+65536].sum(dtype=np.uint64))
        while total >> 32:
            total = (total & 0xffffffff) + (total >> 32)
    return total

def verify_original(path, hdus):
    raw = Path(path).read_bytes()
    end = 0
    for i, hdu in enumerate(hdus):
        info = hdu.fileinfo()
        start, data, span = (info[k] for k in ("hdrLoc", "datLoc", "datSpan"))
        if start != end or data <= start or span < 0 or any(v % 2880 for v in (start,data,span)) or data+span > len(raw):
            raise ValueError("Invalid FITS HDU boundaries")
        end = data+span
        if "CHECKSUM" not in hdu.header or ones_sum(raw[start:end]) != 0xffffffff:
            raise ValueError(f"Missing or invalid original CHECKSUM in HDU {i}")
        if "DATASUM" in hdu.header and ones_sum(raw[data:end]) != int(hdu.header["DATASUM"]):
            raise ValueError(f"Invalid original DATASUM in HDU {i}")
    if end != len(raw):
        raise ValueError("Unexpected trailing FITS bytes")
