"""Synthetic FITS fixtures; not astronomical validation."""
import tempfile
import unittest
from pathlib import Path
from astropy.io import fits
from download_tess import validate_fits

class FitsIdentityTest(unittest.TestCase):
    def fixture(self, path, tic=123, telescope="TESS", quality=True, uncertainty=True):
        primary = fits.PrimaryHDU()
        primary.header["TICID"] = tic
        primary.header["TELESCOP"] = telescope
        primary.header["SECTOR"] = 1
        columns = [fits.Column(name="TIME", format="D", array=[1.0]),
                   fits.Column(name="PDCSAP_FLUX", format="D", array=[1.0])]
        if uncertainty:
            columns.append(fits.Column(name="PDCSAP_FLUX_ERR", format="D", array=[0.001]))
        if quality:
            columns.append(fits.Column(name="QUALITY", format="J", array=[0]))
        fits.HDUList([primary, fits.BinTableHDU.from_columns(columns, name="LIGHTCURVE")]).writeto(path, checksum=True)

    def test_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "fixture.fits"
            self.fixture(path)
            self.assertEqual(validate_fits(path, 123), {"tic":123,"sector":1,"rows":1})
            with self.assertRaises(ValueError):
                validate_fits(path, 124)

    def test_wrong_telescope(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "fixture.fits"
            self.fixture(path, telescope="OTHER")
            with self.assertRaises(ValueError):
                validate_fits(path, 123)

    def test_missing_quality(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "fixture.fits"
            self.fixture(path, quality=False)
            with self.assertRaises(ValueError):
                validate_fits(path, 123)

    def test_missing_uncertainty(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "fixture.fits"
            self.fixture(path, uncertainty=False)
            with self.assertRaisesRegex(ValueError, "Missing required SPOC light curve columns"):
                validate_fits(path, 123)
