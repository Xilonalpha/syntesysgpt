"""Synthetic regression fixture, never observational evidence."""
import argparse
import numpy as np

def generate(output, seed):
    rng = np.random.default_rng(seed)
    t = np.arange(0, 54, .02)
    t = t[(t < 24) | (t > 30)]
    y = np.ones_like(t)
    for period, epoch, depth, duty in [(2.13, .4, .008, .06), (3.47, 1.1, .003, .05)]:
        phase = (t - epoch) / period
        y -= depth * (np.abs(phase - np.round(phase)) < duty / 2)
    y += rng.normal(0, .0003, len(t))
    np.savetxt(output, np.column_stack((2460000 + t, y, np.full_like(t, .0003))),
               delimiter=",", header="bjd,flux,error", comments="", fmt="%.17g")

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("output")
    p.add_argument("--seed", type=int, required=True)
    a = p.parse_args()
    generate(a.output, a.seed)
