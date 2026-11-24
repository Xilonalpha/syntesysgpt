import unittest, tempfile, json
from pathlib import Path
from verify_tess_manifest import verify, digest

class ManifestTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.f = Path(self.tmp.name)/'fixture.fits'; self.f.write_bytes(b'NOT REAL FITS: test fixture')
        self.c = self.f.with_suffix('.csv'); self.c.write_text('bjd,flux,error\n'+''.join(f'{i},1,0.01\n' for i in range(8)))
        self.m = dict(tic=1,source=self.f.name,quality='QUALITY == 0',sha256=digest(self.f),csv_sha256=digest(self.c),normalization=1,points=8)
        self.save()
    def save(self): self.c.with_suffix('.json').write_text(json.dumps(self.m))
    def test_consistency(self): self.assertEqual(verify(self.c,self.f,1),8)
    def test_csv_changed(self):
        self.c.write_text('changed'); self.assertRaises(ValueError,verify,self.c,self.f,1)
    def test_fits_changed(self):
        self.f.write_bytes(b'changed'); self.assertRaises(ValueError,verify,self.c,self.f,1)
    def test_tic(self): self.assertRaises(ValueError,verify,self.c,self.f,2)
    def test_count(self):
        self.m['points']=9; self.save(); self.assertRaises(ValueError,verify,self.c,self.f,1)
    def test_invalid_error(self):
        self.c.write_text(self.c.read_text().replace('0.01','0')); self.m['csv_sha256']=digest(self.c); self.save(); self.assertRaises(ValueError,verify,self.c,self.f,1)
if __name__=='__main__': unittest.main()
