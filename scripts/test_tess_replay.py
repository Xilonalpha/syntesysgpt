import unittest, tempfile, json
from pathlib import Path
from compare_tess_replay import compare
from verify_tess_manifest import digest
class ReplayTest(unittest.TestCase):
    def setUp(self):
        tmp=tempfile.TemporaryDirectory(); self.addCleanup(tmp.cleanup)
        self.a=Path(tmp.name)/'a.csv'; self.b=Path(tmp.name)/'b.csv'
        for p in (self.a,self.b): p.write_text('fixture')
        self.m=dict(tic=1,sector=2,source='fixture.fits',sha256='fixture',csv_sha256=digest(self.a),points=8,normalization=1,quality='QUALITY == 0')
        for p in (self.a,self.b): p.with_suffix('.json').write_text(json.dumps(self.m))
    def test_match(self): compare(self.a,self.b)
    def test_changed_csv(self):
        self.b.write_text('different'); self.assertRaises(ValueError,compare,self.a,self.b)
    def test_changed_sector(self):
        self.m['sector']=3; self.b.with_suffix('.json').write_text(json.dumps(self.m)); self.assertRaises(ValueError,compare,self.a,self.b)
    def test_changed_normalization(self):
        self.m['normalization']=2; self.b.with_suffix('.json').write_text(json.dumps(self.m)); self.assertRaises(ValueError,compare,self.a,self.b)
