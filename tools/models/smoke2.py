"""Cauliflower smoke puffs: union of soft sub-blobs + domain-warped noise, lit from the top with self-shadowing.
Output: luminance (0..1) and alpha (0..1), premultiplied later by the renderer."""
import math
import numpy as np
from scipy.ndimage import gaussian_filter, map_coordinates


def fbm(n, seed, beta=2.0, lo=2.0):
    r = np.random.default_rng(seed)
    wn = r.standard_normal((n, n))
    fy = np.fft.fftfreq(n)[:, None] * n; fx = np.fft.fftfreq(n)[None, :] * n
    f = np.sqrt(fx * fx + fy * fy); f[0, 0] = 1
    amp = 1 / np.maximum(f, lo) ** (beta / 2); amp[0, 0] = 0
    o = np.real(np.fft.ifft2(np.fft.fft2(wn) * amp))
    o -= o.mean(); return o / (np.abs(o).max() + 1e-9)


def puff(n=256, seed=0, blobs=9, soft=1.0):
    r = np.random.default_rng(seed)
    y, x = (np.mgrid[0:n, 0:n] + 0.5) / n * 2 - 1
    # domain warp for wispy, non-circular edges
    wx, wy = fbm(n, seed + 1, 2.4) * 0.10, fbm(n, seed + 2, 2.4) * 0.10
    xw, yw = x + wx, y + wy
    dens = np.zeros((n, n))
    for k in range(blobs):
        a = r.uniform(0, 2 * math.pi); rr = r.uniform(0.0, 0.42) * (0.4 if k == 0 else 1)
        cx, cy = math.cos(a) * rr, math.sin(a) * rr * 0.85 - 0.04
        rad = r.uniform(0.30, 0.47) * (1.25 if k == 0 else 1)
        d2 = ((xw - cx) ** 2 + (yw - cy) ** 2) / (rad * rad)
        dens = np.maximum(dens, np.exp(-d2 * 1.6))
    detail = fbm(n, seed + 3, 1.7, 6)
    dens = dens * (0.85 + 0.3 * detail)
    dens *= np.clip((1 - np.sqrt(x * x + y * y)) * 4, 0, 1)          # never touch the sprite border
    dens = gaussian_filter(dens, n / 180 * soft)
    alpha = np.clip((dens - 0.08) / 0.5, 0, 1)
    alpha = alpha * alpha * (3 - 2 * alpha)
    # lighting: height = density, light from the top (image -y), soft self-shadow from density above
    h = gaussian_filter(dens, n / 40)
    gy, gx = np.gradient(h)
    nz = 0.08
    nx, ny = -gx * n * 0.5, -gy * n * 0.5
    ln = np.sqrt(nx * nx + ny * ny + nz * nz)
    lam = (-ny / ln) * 0.5 + 0.5                                      # facing up -> 1
    shadow = np.zeros_like(h)
    for k in range(1, 9):
        shadow += np.roll(h, k * n // 48, axis=0)
    shadow = np.clip(shadow / 8 - h * 0.6, 0, 1)
    lum = 0.80 + 0.22 * (lam - 0.5) - 0.28 * shadow + 0.06 * detail
    return np.clip(lum, 0.45, 1.0), np.clip(alpha, 0, 1)
