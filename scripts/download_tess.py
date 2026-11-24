"""Download a SPOC light curve from MAST, without synthetic fallback."""
import argparse
from pathlib import Path



def exact_products(products, tic):
    return sorted({str(row["dataURI"]) for row in products
                   if str(row["productFilename"]).endswith("_lc.fits")
                   and str(row["dataURI"]).startswith("mast:TESS/product/")
                   and f"-{tic:016d}-" in str(row["dataURI"]).rsplit("/", 1)[-1]})


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--tic', type=int, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    if a.tic <= 0 or a.output.exists():
        p.error('Positive TIC and new output directory required')
    from astroquery.mast import Observations
    obs = Observations.query_criteria(obs_collection='TESS', provenance_name='SPOC', target_name=str(a.tic))
    if not len(obs):
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
    if not candidates:
        raise RuntimeError('No SPOC light curve found')
    a.output.mkdir(parents=True)
    uri = candidates[0]
    path = a.output / uri.rsplit('/', 1)[-1]
    status, message, url = Observations.download_file(uri, local_path=str(path), cache=False)
    if status != 'COMPLETE' or not path.is_file():
        raise RuntimeError(f'MAST download failed: {status} {message}')
    (a.output / 'source_uri.txt').write_text(uri + '\n')
    print(path)


if __name__ == '__main__':
    main()
