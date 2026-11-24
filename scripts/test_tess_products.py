import unittest
from download_tess import exact_products

class ProductTest(unittest.TestCase):
    def row(self, tic, suffix='_lc.fits', prefix='mast:TESS/product/'):
        name=f'tess202000-s0001-{tic:016d}-0123-s{suffix}'
        return dict(productFilename=name,dataURI=prefix+name)
    def test_exact(self):
        a=self.row(123)
        self.assertEqual(exact_products([a,self.row(124)],123),[a['dataURI']])
    def test_wrong_type(self):
        self.assertEqual(exact_products([self.row(123,'_tp.fits')],123),[])
    def test_wrong_origin(self):
        self.assertEqual(exact_products([self.row(123,prefix='other/')],123),[])
    def test_duplicate(self):
        a=self.row(123)
        self.assertEqual(exact_products([a,a],123),[a['dataURI']])
