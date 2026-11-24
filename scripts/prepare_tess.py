"""Convert a locally supplied SPOC light curve; never generate replacement data."""
import argparse, hashlib, json
from pathlib import Path
import numpy as np
from astropy.io import fits
p=argparse.ArgumentParser(); p.add_argument('fits'); p.add_argument('csv'); p.add_argument('--tic',type=int,required=True); a=p.parse_args()
src=Path(a.fits); dst=Path(a.csv)
with fits.open(src, checksum=True) as h:
    if int(h[0].header['TICID']) != a.tic: raise ValueError('TIC mismatch')
    for unit in h:
        if 'CHECKSUM' in unit.header and unit.verify_checksum() != 1: raise ValueError('Invalid FITS checksum')
        if 'DATASUM' in unit.header and unit.verify_datasum() != 1: raise ValueError('Invalid FITS datasum')
    hdr=h[1].header; d=h[1].data
    if hdr.get('TIMESYS')!='TDB': raise ValueError('Expected TDB')
    t=np.asarray(d['TIME'],float)+float(hdr['BJDREFI'])+float(hdr['BJDREFF'])
    y=np.asarray(d['PDCSAP_FLUX'],float); e=np.asarray(d['PDCSAP_FLUX_ERR'],float)
    good=(d['QUALITY']==0)&np.isfinite(t)&np.isfinite(y)&np.isfinite(e)&(e>0)
    t,y,e=t[good],y[good],e[good]; order=np.argsort(t); t,y,e=t[order],y[order],e[order]
    if len(t)<8 or np.any(np.diff(t)<=0): raise ValueError('Invalid time series')
    norm=float(np.median(y))
    if not np.isfinite(norm) or norm <= 0: raise ValueError("Invalid normalization")
    np.savetxt(dst,np.column_stack((t,y/norm,e/norm)),delimiter=',',header='bjd,flux,error',comments='',fmt='%.17g')
    dst.with_suffix('.json').write_text(json.dumps({'tic':a.tic,'sector':int(h[0].header['SECTOR']),'source':src.name,'sha256':hashlib.sha256(src.read_bytes()).hexdigest(),'csv_sha256':hashlib.sha256(dst.read_bytes()).hexdigest(),'points':len(t),'normalization':norm,'quality':'QUALITY == 0'},indent=2))
