"""Sprites for the custom VFX: smoke puffs (alpha blended, lit), fire/glow/plume/spark/ring/flare (additive,
premultiplied on black: the additive layer adds RGB and ignores alpha), scorch decal."""
import math

import numpy as np
from PIL import Image, ImageFilter

from tex import noise, band, rng, to_img, lerp


def _grid(n):
    y, x = np.mgrid[0:n, 0:n]
    return (x + 0.5) / n * 2 - 1, (y + 0.5) / n * 2 - 1


def _blur(a, sigma):
    from scipy.ndimage import gaussian_filter
    return gaussian_filter(np.asarray(a, dtype=float), sigma, mode='constant')


def _warped(n, seed, beta=1.9, warp=0.12):
    """domain-warped fractal noise in [-0.5, 0.5] (cloudy, fractal edges)"""
    from scipy.ndimage import map_coordinates
    base = noise(n, seed, beta) - 0.5
    wx = (noise(n, seed + 1, 2.4) - 0.5) * warp * n
    wy = (noise(n, seed + 2, 2.4) - 0.5) * warp * n
    yy, xx = np.mgrid[0:n, 0:n].astype(float)
    return map_coordinates(base, [yy + wy, xx + wx], order=1, mode='wrap')


def puff(n, seed):
    """one cloudy smoke puff: (luminance, alpha). Radial falloff + domain-warped fractal noise gives an irregular,
    billowy silhouette with soft wisps; lit from the top with a cheap self-shadow."""
    r = rng(seed)
    x, y = _grid(n)
    ox, oy = r.normal(0, 0.06, 2)
    sx, sy = r.uniform(0.85, 1.15, 2)
    d = np.sqrt(((x - ox) / sx) ** 2 + ((y - oy) / sy) ** 2)
    fbm = _warped(n, seed, 1.8, 0.10)
    billow = np.abs(_warped(n, seed + 7, 2.2, 0.08)) * 2
    shape = (1.0 - d / 0.82) + fbm * 1.15
    dens = np.clip(shape / 0.55, 0, 1)
    dens = dens * dens * (3 - 2 * dens)
    dens *= 0.72 + 0.28 * (1 - billow)
    dens *= np.clip((1 - d) * 3, 0, 1)
    soft = _blur(dens, n / 28)
    gy, gx = np.gradient(soft)
    shadow = np.zeros_like(dens)
    for k in range(1, 7):
        shadow += np.roll(soft, k * n // 36, axis=0)
    shadow /= 6
    lum = 0.74 + np.clip((gy + gx * 0.3) * n * 0.07, -0.3, 0.26) - 0.32 * np.clip(shadow - soft * 0.4, 0, 1) + 0.1 * fbm
    lum = np.clip(lum, 0.3, 1.0)
    alpha = np.clip(dens * 0.95, 0, 1)
    alpha = np.where(alpha < 0.11, 0.0, alpha)
    return lum, alpha


def smoke_atlas(seed=100, cells=8, n=256):
    big = np.zeros((cells * n, cells * n, 4))
    for i in range(cells * cells):
        lum, al = puff(n, seed + i * 7)
        cy, cx = divmod(i, cells)
        big[cy * n:(cy + 1) * n, cx * n:(cx + 1) * n, :3] = lum[..., None] * np.array([1.0, 0.985, 0.96])
        big[cy * n:(cy + 1) * n, cx * n:(cx + 1) * n, 3] = al
    return Image.fromarray((big * 255 + 0.5).clip(0, 255).astype(np.uint8), 'RGBA')


def fire_ramp(t):
    """temperature 0..1 -> premultiplied colour"""
    stops = [(0.0, (0, 0, 0)), (0.18, (0.25, 0.03, 0.0)), (0.38, (0.85, 0.22, 0.02)), (0.6, (1.0, 0.55, 0.12)),
             (0.8, (1.0, 0.85, 0.45)), (1.0, (1.0, 1.0, 0.92))]
    t = np.clip(t, 0, 1)
    out = np.zeros(t.shape + (3,))
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        m = (t >= a) & (t <= b)
        f = ((t - a) / (b - a))[..., None]
        out = np.where(m[..., None], np.asarray(ca) * (1 - f) + np.asarray(cb) * f, out)
    return out


def flame(n, seed):
    r = rng(seed)
    x, y = _grid(n)
    d = np.sqrt(x * x + y * y)
    fbm = _warped(n, seed, 1.7, 0.14)
    fil = np.abs(_warped(n, seed + 3, 2.0, 0.1)) * 2
    shape = (1.0 - d / 0.8) + fbm * 1.3
    t = np.clip(shape / 0.7, 0, 1)
    t = t * (0.75 + 0.35 * (1 - fil))
    t *= np.clip((1 - d) * 3, 0, 1)
    t = _blur(t, n / 120)
    return fire_ramp(np.clip(t * 1.15, 0, 1))


def fire_atlas(seed=300, cells=8, n=256):
    big = np.zeros((cells * n, cells * n, 3))
    for i in range(cells * cells):
        cy, cx = divmod(i, cells)
        big[cy * n:(cy + 1) * n, cx * n:(cx + 1) * n] = flame(n, seed + i * 13)
    return Image.fromarray((big * 255 + 0.5).clip(0, 255).astype(np.uint8), 'RGB').convert('RGBA')


def glow(n=256):
    x, y = _grid(n)
    d = np.sqrt(x * x + y * y)
    core = np.exp(-d * d * 18)
    halo = np.exp(-d * 4.0) * 0.55
    v = np.clip(core + halo, 0, 1) * np.clip(1 - d, 0, 1) ** 0.5
    return to_img(np.dstack([v, v * 0.97, v * 0.9]))


def flare(n=512):
    x, y = _grid(n)
    d = np.sqrt(x * x + y * y)
    a = np.arctan2(y, x)
    rays = np.clip(np.cos(a * 6) ** 40 + np.cos(a * 6 + math.pi / 6) ** 60 * 0.5, 0, 1) * np.exp(-d * 2.2)
    core = np.exp(-d * d * 30) + np.exp(-d * 6) * 0.35
    v = np.clip(core + rays * 0.8, 0, 1) * np.clip(1 - d, 0, 1)
    return to_img(np.dstack([v, v, v * 0.95]))


def plume(w=256, h=1024, seed=500):
    """axial exhaust flame, nozzle at the top (v = 0), tail at the bottom; shock diamonds near the nozzle"""
    y, x = np.mgrid[0:h, 0:w]
    v = (y + 0.5) / h
    u = (x + 0.5) / w * 2 - 1
    width = 0.32 + 0.68 * np.clip(v * 2.2, 0, 1) ** 0.6
    core_w = 0.18 + 0.25 * v
    prof = np.exp(-(u / width) ** 2 * 3.2)
    core = np.exp(-(u / core_w) ** 2 * 4.0) * np.clip(1 - v * 1.6, 0, 1)
    # Mach diamonds: periodic bright nodes along the axis near the nozzle
    diamonds = (0.5 + 0.5 * np.cos(v * 2 * math.pi * 7)) ** 6 * np.clip(1 - v * 2.4, 0, 1) * np.exp(-(u / 0.12) ** 2)
    turb = noise((h, w), seed, 2.0, aspect=(1, 4))
    t = (prof * (1 - v) ** 1.4 * (0.75 + 0.45 * turb) + core * 0.9 + diamonds * 0.8)
    col = fire_ramp(np.clip(t * 1.1, 0, 1))
    col *= np.clip(1 - v, 0, 1)[..., None] ** 0.8
    return Image.fromarray((col * 255).clip(0, 255).astype(np.uint8), 'RGB').convert('RGBA')


def spark(w=64, h=256):
    y, x = np.mgrid[0:h, 0:w]
    v = (y + 0.5) / h
    u = (x + 0.5) / w * 2 - 1
    t = np.exp(-u * u * 18) * (np.sin(v * math.pi) ** 0.7) * (0.4 + 0.6 * v)
    return to_img(fire_ramp(np.clip(t * 1.3, 0, 1)))


def ring(n=1024):
    x, y = _grid(n)
    d = np.sqrt(x * x + y * y)
    nz = noise(n, 900, 2.0)
    v = np.exp(-((d - 0.93) / 0.035) ** 2) * (0.7 + 0.5 * nz) + np.exp(-((d - 0.86) / 0.09) ** 2) * 0.25
    v *= (d < 1.0)
    return to_img(np.dstack([v, v, v]))


def scorch(n=1024, seed=950):
    x, y = _grid(n)
    d = np.sqrt(x * x + y * y)
    nz = noise(n, seed, 2.2)
    edge = 0.72 + 0.18 * (noise(n, seed + 1, 1.6) - 0.5)
    a = np.clip((edge - d) / 0.25, 0, 1) ** 1.2 * (0.75 + 0.3 * nz)
    col = lerp(np.array([0.05, 0.045, 0.04]), np.array([0.2, 0.17, 0.13]), nz[..., None])
    return to_img(col, np.clip(a, 0, 1))


def debris_tex(n=256, seed=980):
    lo = noise(n, seed, 2.2)
    col = lerp(np.array([0.22, 0.2, 0.17]), np.array([0.45, 0.4, 0.33]), lo[..., None])
    x, y = _grid(n)
    a = np.arctan2(y, x)
    edge = .63 + .12*np.sin(a*5+1) + .07*np.cos(a*9)
    alpha = np.clip((edge-np.sqrt(x*x+y*y))*n/3,0,1)
    return to_img(col, alpha)
