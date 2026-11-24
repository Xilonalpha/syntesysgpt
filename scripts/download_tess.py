"""Download a SPOC light curve from MAST, without synthetic fallback."""
import argparse
import hashlib
import json
import warnings
from pathlib import Path
from fits_integrity import verify_original



def exact_products(products, tic):
    return sorted({str(row["dataURI"]) for row in products
                   if str(row["productFilename"]).endswith("_lc.fits")
                   and str(row["dataURI"]).startswith("mast:TESS/product/")
                   and str(row["productFilename"]) == str(row["dataURI"]).rsplit("/", 1)[-1]
                   and f"-{tic:016d}-" in str(row["dataURI"]).rsplit("/", 1)[-1]})


def validate_fits(path, tic):
    from astropy.io import fits
    with warnings.catch_warnings():
        warnings.simplefilter("error")
        with fits.open(path, checksum=False) as hdus:
            verify_original(path, hdus)
            hdus.verify("exception")
            header = hdus[0].header
            if int(header.get("TICID", -1)) != tic:
                raise ValueError("FITS TICID does not match requested target")
            if str(header.get("TELESCOP", "")).strip().upper() != "TESS":
                raise ValueError("FITS telescope is not TESS")
            if "LIGHTCURVE" not in hdus:
                raise ValueError("Missing LIGHTCURVE extension")
            data = hdus["LIGHTCURVE"].data
            if data is None or len(data) == 0:
                raise ValueError("Empty light curve")
            if not {"TIME", "PDCSAP_FLUX", "PDCSAP_FLUX_ERR", "QUALITY"}.issubset(data.names):
                raise ValueError("Missing required SPOC light curve columns")
            sector = int(header["SECTOR"])
            if sector <= 0:
                raise ValueError("Invalid TESS sector")
            return {"tic": tic, "sector": sector, "rows": len(data)}


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--tic', type=int, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--sector', type=int)
    a = p.parse_args()
    if a.sector is not None and a.sector <= 0:
        p.error('Positive sector required')
    if a.tic <= 0 or a.output.exists():
        p.error('Positive TIC and new output directory required')
    from astroquery.mast import Observations
    obs = Observations.query_criteria(obs_collection='TESS', provenance_name='SPOC', target_name=str(a.tic))
    candidates = exact_products(Observations.get_product_list(obs), a.tic) if len(obs) else []
    if not candidates:
        # Resolve the TIC to sky coordinates; CAOM target names are not always TIC IDs.
        from astroquery.mast import Catalogs
        from astropy.coordinates import SkyCoord
        import astropy.units as u
        catalog = Catalogs.query_criteria(catalog='Tic', ID=a.tic)
        if len(catalog) != 1 or int(catalog[0]['ID']) != a.tic:
            raise RuntimeError('TIC identity could not be resolved uniquely')
        coord = SkyCoord(float(catalog[0]['ra']), float(catalog[0]['dec']), unit='deg')
        obs = Observations.query_region(coord, radius=0.01*u.deg)
        obs = obs[(obs['obs_collection'] == 'TESS') & (obs['provenance_name'] == 'SPOC')]
        print(f'Coordinate search returned {len(obs)} SPOC observations')
        if not len(obs):
            raise RuntimeError('No SPOC observations found for resolved TIC')
        products = Observations.get_product_list(obs)
        candidates = exact_products(products, a.tic)
    if a.sector is not None:
        candidates = [uri for uri in candidates if f'-s{a.sector:04d}-' in uri.rsplit('/', 1)[-1]]
    if not candidates:
        raise RuntimeError('No SPOC light curve found for requested selection')
    a.output.mkdir(parents=True)
    attempts = []
    metadata = None
    for uri in candidates:
        path = a.output / uri.rsplit('/', 1)[-1]
        for attempt in range(1, 3):
            record = {"uri": uri, "attempt": attempt}
            try:
                status, message, url = Observations.download_file(uri, local_path=str(path), cache=False)
                if status != 'COMPLETE' or not path.is_file():
                    raise RuntimeError(f'MAST download failed: {status} {message}')
                record["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
                verified = validate_fits(path, a.tic)
                if a.sector is not None and verified['sector'] != a.sector:
                    raise ValueError('FITS sector mismatch')
                metadata = verified
                record["status"] = "accepted"
            except Exception as exc:
                record.update(status="rejected", error=f"{type(exc).__name__}: {exc}")
                path.unlink(missing_ok=True)
                print(f"Rejected {uri}, attempt {attempt}: {exc}", flush=True)
            finally:
                attempts.append(record)
                (a.output / 'download_attempts.json').write_text(json.dumps(attempts, indent=2) + '\n')
            if metadata is not None:
                break
        if metadata is not None:
            break
    if metadata is None:
        raise RuntimeError('No exact SPOC product passed integrity validation')
    metadata.update(source_uri=uri, candidate_uris=candidates,
                    selection_policy="first_integrity_valid_exact_product_sorted_uri_two_attempts",
                    sha256=hashlib.sha256(path.read_bytes()).hexdigest())
    (a.output / 'download_manifest.json').write_text(json.dumps(metadata, indent=2) + '\n')
    (a.output / 'source_uri.txt').write_text(uri + '\n')
    print(path)


if __name__ == '__main__':
    main()
