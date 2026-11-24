import unittest
import tempfile
from pathlib import Path
import json
import numpy as np
from stellar_detrend import detrend
from apply_stellar_detrend import apply

class DetrendInjectionTest(unittest.TestCase):
    def test_differential_injections(self):
        t = np.arange(3000)*.01
        raw = 1 + .004*np.sin(2*np.pi*t/3.2)
        e = np.full(len(t), .0005)
        control, _, _, _ = detrend(t, raw, e)
        for depth in (.0002, .0006, .0015):
            for duration in (.04, .08, .16):
                inside = abs((t-.7+1.1)%2.2-1.1)<duration/2
                injected = raw*(1-depth*inside)
                filtered, _, _, support = detrend(t, injected, e)
                ratio = np.median((control-filtered)[inside & support])/depth
                self.assertTrue(.8 <= ratio <= 1.2, (depth,duration,ratio))

    def test_artifact_preservation(self):
        with tempfile.TemporaryDirectory() as d:
            src, dst = Path(d)/"raw.csv", Path(d)/"filtered.csv"
            t = 2459000 + np.arange(400)*.01
            np.savetxt(src, np.column_stack((t,np.ones(400),np.full(400,.001))),
                       delimiter=",", header="bjd,flux,error", comments="")
            before = src.read_bytes()
            apply(src,dst)
            self.assertEqual(before,src.read_bytes())
            report = json.loads(Path(str(dst)+".json").read_text())
            self.assertEqual(report["output_points"],400)
            self.assertEqual(report["global_fap"],"NOT_CALIBRATED")
            with self.assertRaises(ValueError):
                apply(src,dst)

if __name__ == "__main__":
    unittest.main()
