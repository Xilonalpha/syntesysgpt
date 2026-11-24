import unittest
from verify_planet_reference import validate

class PlanetReferenceTest(unittest.TestCase):
    def setUp(self):
        self.row = {"pl_name": "Example b", "tic_id": "TIC 123", "pl_orbper": 2.0}

    def test_match(self):
        self.assertEqual(validate([self.row], "Example b", 123, 2.0), self.row)

    def test_wrong_tic(self):
        with self.assertRaises(ValueError):
            validate([self.row], "Example b", 124, 2.0)

    def test_wrong_period(self):
        with self.assertRaises(ValueError):
            validate([self.row], "Example b", 123, 2.1)

    def test_wrong_name(self):
        with self.assertRaises(ValueError):
            validate([self.row], "Example c", 123, 2.0)

    def test_missing_identity(self):
        self.row["tic_id"] = None
        with self.assertRaises(ValueError):
            validate([self.row], "Example b", 123, 2.0)

    def test_duplicate(self):
        with self.assertRaises(ValueError):
            validate([self.row, self.row], "Example b", 123, 2.0)

    def test_nonfinite(self):
        with self.assertRaises(ValueError):
            validate([self.row], "Example b", 123, float("nan"))

if __name__ == "__main__":
    unittest.main()
