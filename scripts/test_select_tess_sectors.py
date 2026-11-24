import unittest
from select_tess_sectors import select

class SectorSelectionTest(unittest.TestCase):
    def test_longest(self):
        self.assertEqual(select([9,2,1,8,10,9,11]),[8,9,10])
    def test_tie(self):
        self.assertEqual(select([9,10,1,2]),[1,2])
    def test_gap(self):
        with self.assertRaises(ValueError):
            select([1,4,9])
    def test_invalid(self):
        for v in ([True,2],[0,1],["1",2],[]):
            with self.assertRaises(ValueError):
                select(v)
