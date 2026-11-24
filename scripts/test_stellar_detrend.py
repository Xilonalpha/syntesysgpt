import unittest
import numpy as np
from stellar_detrend import detrend

class StellarDetrendTest(unittest.TestCase):
    def test_constant(self):
        t = np.arange(400) * .01
        y, e, b, support = detrend(t, np.full(400, 2.), np.full(400, .002))
        self.assertTrue(support.all())
        np.testing.assert_allclose(y, 1.)
        np.testing.assert_allclose(e, .001)

    def test_gap_isolation(self):
        t = np.r_[np.arange(400)*.01, 100+np.arange(400)*.01]
        y, _, b, support = detrend(t, np.repeat([1., 2.], 400), np.full(800, .001))
        self.assertTrue(support.all())
        np.testing.assert_allclose(y, 1.)
        np.testing.assert_allclose(b, np.repeat([1., 2.], 400))

    def test_short_transit(self):
        t = np.arange(1000)*.01
        flux = 1 + .005*np.sin(2*np.pi*t/5)
        inside = abs(t-5)<.05
        flux[inside] *= .99
        original = flux.copy()
        y, _, _, support = detrend(t, flux, np.full(len(t), .001))
        self.assertTrue(support[inside].all())
        self.assertGreater(1-np.median(y[inside]), .008)
        np.testing.assert_array_equal(flux, original)

    def test_invalid_errors(self):
        with self.assertRaises(ValueError):
            detrend(np.arange(20), np.ones(20), np.zeros(20))

    def test_unsupported(self):
        t = np.arange(20)*2.
        y, _, _, support = detrend(t, np.ones(20)*2, np.ones(20))
        self.assertFalse(support.any())
        np.testing.assert_allclose(y, 2.)

if __name__ == "__main__":
    unittest.main()
