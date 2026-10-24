"""Texture work for the converted models: recolour, normal-map cavity baked into albedo (Minecraft has no normal
maps), grime, ambient occlusion maps, LOD chain. Everything derives from the CC-BY source textures or is procedural."""
import numpy as np
from PIL import Image, ImageFilter
from scipy.ndimage import gaussian_filter, map_coordinates

import os
SRC = os.environ.get('SILO_MODEL_SRC', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'src'))


def f(im):
    return np.asarray(im, np.float32) / 255.0


def tileable_noise(n, seed, beta=2.0, lo=1.0):
    r = np.random.default_rng(seed)
    wn = r.standard_normal((n, n))
    fy = np.fft.fftfreq(n)[:, None] * n
    fx = np.fft.fftfreq(n)[None, :] * n
    fr = np.sqrt(fx * fx + fy * fy); fr[0, 0] = 1
    amp = 1 / np.maximum(fr, lo) ** (beta / 2); amp[0, 0] = 0
    o = np.real(np.fft.ifft2(np.fft.fft2(wn) * amp)); o -= o.min(); return o / max(1e-9, o.max())


def cavity_from_normal(nm, strength=1.6, blur=0.6):
    """normal map (tangent space, 0..1) -> multiplier: panel lines and edges get darker, flat stays 1"""
    n = nm[..., :3] * 2 - 1
    flat = np.clip(n[..., 2], 0, 1)
    dev = 1 - flat
    if blur:
        dev = gaussian_filter(dev, blur)
    return np.clip(1 - dev * strength, 0.35, 1.0)


def edge_highlight(nm, strength=0.35):
    """light from the top-left on the normal map: subtle bevel highlight so details read under flat lighting"""
    n = nm[..., :3] * 2 - 1
    l = np.array([-0.45, 0.55, 0.7]); l /= np.linalg.norm(l)
    d = (n @ l) - l[2]
    return np.clip(1 + d * strength, 0.7, 1.3)


def tomahawk_albedo(size=2048):
    base = f(Image.open(f'{SRC}/tomahawk/textures/lambert5_baseColor.png').convert('RGB'))
    nm = f(Image.open(f'{SRC}/tomahawk/textures/lambert5_normal.png').convert('RGB'))
    r, g, b = base[..., 0], base[..., 1], base[..., 2]
    orange = (r > 0.55) & (g < 0.45) & (b < 0.25)
    soft = gaussian_filter(orange.astype(np.float32), 1.0)[..., None]
    # service grey (FS 36375-like) for the orange test-round panels; the light grey stays
    grey = np.array([0.50, 0.53, 0.56], np.float32)
    col = base * (1 - soft) + grey * soft
    # slightly warmer, less sterile light grey
    lg = (np.abs(r - g) < 0.04) & (r > 0.62)
    col = np.where(lg[..., None], col * np.array([0.93, 0.94, 0.95]), col)
    n = col.shape[0]
    grime = tileable_noise(n, 11, 2.3)
    fine = tileable_noise(n, 12, 1.2, 40)
    col *= (0.93 + 0.1 * grime[..., None]) * (0.97 + 0.05 * fine[..., None])
    col *= cavity_from_normal(nm, 1.8)[..., None] * edge_highlight(nm, 0.4)[..., None]
    streak = tileable_noise(n, 13, 2.6)
    streak = gaussian_filter(streak, (18, 1.5))
    col *= (1 - 0.06 * np.clip((streak - 0.55) * 4, 0, 1))[..., None]
    return Image.fromarray((np.clip(col, 0, 1) * 255 + 0.5).astype(np.uint8)).resize((size, size), Image.LANCZOS)


def perst_albedo(size=1024):
    base = Image.open(f'{SRC}/perst4/textures/Material.001_baseColor.png').convert('RGBA').resize((2048, 2048), Image.LANCZOS)
    orm = f(Image.open(f'{SRC}/perst4/textures/Material.001_metallicRoughness.png').convert('RGB').resize((2048, 2048), Image.LANCZOS))
    nm = f(Image.open(f'{SRC}/perst4/textures/Material.001_normal.jpeg').convert('RGB').resize((2048, 2048), Image.LANCZOS))
    col = f(base)[..., :3]
    ao = 0.35 + 0.65 * orm[..., 0]
    metal = orm[..., 2]
    col = col * ao[..., None] * cavity_from_normal(nm, 1.2, 0.8)[..., None] * edge_highlight(nm, 0.55)[..., None]
    # metal reads too dark without reflections: lift it a little toward a cool sheen
    col = col * (1 + 0.25 * metal[..., None]) + 0.04 * metal[..., None]
    return Image.fromarray((np.clip(col, 0, 1) * 255 + 0.5).astype(np.uint8)).resize((size, size), Image.LANCZOS)


def painted_steel(n=1024, seed=40):
    """neutral detail map (tinted per material by vertex colour): paint orange peel, chips, grime, scratches"""
    lo = tileable_noise(n, seed, 2.2)
    mid = tileable_noise(n, seed + 1, 1.6, 8)
    fine = tileable_noise(n, seed + 2, 0.8, 60)
    v = 0.86 + 0.10 * lo + 0.05 * mid + 0.03 * fine
    chips = np.clip((tileable_noise(n, seed + 3, 1.4, 20) - 0.83) * 9, 0, 1) * np.clip((lo - 0.35) * 3, 0, 1)
    grime = np.clip((tileable_noise(n, seed + 4, 2.4) - 0.5) * 2.2, 0, 1)
    rgb = np.dstack([v, v, v])
    rgb = rgb * (1 - 0.22 * grime[..., None]) + np.array([0.20, 0.18, 0.15]) * 0.22 * grime[..., None]
    rgb = rgb * (1 - chips[..., None]) + np.array([0.42, 0.40, 0.38]) * chips[..., None]
    return Image.fromarray((np.clip(rgb, 0, 1) * 255 + 0.5).astype(np.uint8))


def grip_pad(n=512, seed=50):
    yy, xx = np.mgrid[0:n, 0:n]
    dots = ((np.sin(xx / n * 2 * np.pi * 32) > 0.55) & (np.sin(yy / n * 2 * np.pi * 32) > 0.55)).astype(np.float32)
    v = 0.16 + 0.05 * tileable_noise(n, seed, 2.0) + 0.08 * gaussian_filter(dots, 0.7)
    wear = np.clip((tileable_noise(n, seed + 1, 2.2) - 0.55) * 2, 0, 1)
    v = v * (1 - 0.3 * wear) + 0.3 * 0.3 * wear
    return Image.fromarray((np.clip(np.dstack([v, v, v * 0.97]), 0, 1) * 255 + 0.5).astype(np.uint8))


def concrete(n=1024, seed=60):
    lo = tileable_noise(n, seed, 2.3)
    pores = np.clip((tileable_noise(n, seed + 1, 0.6, 120) - 0.78) * 6, 0, 1)
    stain = np.clip((tileable_noise(n, seed + 2, 2.6) - 0.55) * 2, 0, 1)
    v = 0.56 + 0.12 * lo - 0.12 * pores - 0.10 * stain
    rgb = np.dstack([v * 1.0, v * 0.985, v * 0.95])
    return Image.fromarray((np.clip(rgb, 0, 1) * 255 + 0.5).astype(np.uint8))


def lod_chain(img, sizes):
    return {s: img.resize((s, s), Image.LANCZOS) for s in sizes}


def pad_decal(n=1024, extent=(-1.3, 1.3, -1.35, 1.6), deck=(-0.73, 0.73, -0.94, 1.15), seed=70):
    """unique top texture of the concrete headworks: hazard border, stencil, soot, contact AO near the deck"""
    from PIL import ImageDraw, ImageFont
    x0, x1, z0, z1 = extent
    base = np.asarray(concrete(n, seed), np.float32) / 255
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    X = x0 + (xx + 0.5) / n * (x1 - x0)
    Z = z0 + (yy + 0.5) / n * (z1 - z0)
    dx = np.maximum(np.maximum(deck[0] - X, X - deck[1]), 0)
    dz = np.maximum(np.maximum(deck[2] - Z, Z - deck[3]), 0)
    d = np.hypot(dx, dz)                      # distance outside the deck rectangle (m)
    col = base.copy()
    # hazard border 0.07..0.19 m outside the deck
    band = (d > 0.07) & (d < 0.19)
    stripe = (((X + Z) / 0.16) % 1.0) < 0.5
    yellow = np.array([0.78, 0.62, 0.12]); black = np.array([0.09, 0.09, 0.08])
    wear = tileable_noise(n, seed + 5, 1.6, 6)
    paint_a = (0.85 * np.clip(1.2 - wear * 0.9, 0, 1))[..., None]
    hz = np.where(stripe[..., None], yellow, black)
    col = np.where(band[..., None], col * (1 - paint_a) + hz * paint_a, col)
    # contact shadow / grime against the steel deck, soot streaks radiating from the mouth
    col *= (1 - 0.45 * np.exp(-d / 0.05))[..., None]
    ang = np.arctan2(Z - 0.1, X)
    rays = 0.5 + 0.5 * np.sin(ang * 23 + tileable_noise(n, seed + 6, 2.0) * 6)
    soot = np.exp(-d / 0.5) * (0.25 + 0.35 * rays) * (d > 0.19)
    col *= (1 - 0.35 * soot)[..., None]
    img = Image.fromarray((np.clip(col, 0, 1) * 255 + 0.5).astype(np.uint8))
    # stencil along the front edge
    dr = ImageDraw.Draw(img)
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf', 46)
    for text, zc in (('ОПАСНО  ПУСКОВАЯ ШАХТА', 1.42), ('НЕ СТОЯТЬ НА ЛЮКЕ', -1.22)):
        layer = Image.new('L', img.size, 0)
        ImageDraw.Draw(layer).text((n / 2, (zc - z0) / (z1 - z0) * n), text, font=font, fill=255, anchor='mm')
        m = np.asarray(layer, np.float32)[..., None] / 255 * np.clip(1.15 - wear[..., None], 0, 1) * 0.8
        a = np.asarray(img, np.float32) / 255
        a = a * (1 - m) + np.array([0.80, 0.66, 0.16]) * m
        img = Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8))
    return img
