"""Original, tileable procedural HD textures. Reproducible seeded artwork; font notices accompany the project."""
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

INNER = 496          # tileable area of a 512 tile, 8 px wrapped gutters on every side
FONT_BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf'
FONT_MONO = '/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf'


def rng(seed):
    return np.random.default_rng(seed)


def noise(size, seed, beta=2.0, lo=1.0, aspect=(1, 1)):
    """tileable fractal noise in [0,1]: white noise filtered by 1/f^beta in the frequency domain"""
    h, w = (size, size) if isinstance(size, int) else size
    r = rng(seed)
    wn = r.standard_normal((h, w))
    fy = np.fft.fftfreq(h)[:, None] * h / aspect[0]
    fx = np.fft.fftfreq(w)[None, :] * w / aspect[1]
    f = np.sqrt(fx * fx + fy * fy)
    f[0, 0] = 1.0
    amp = 1.0 / np.maximum(f, lo) ** (beta / 2)
    amp[0, 0] = 0
    out = np.real(np.fft.ifft2(np.fft.fft2(wn) * amp))
    out -= out.min()
    out /= max(1e-9, out.max())
    return out


def band(size, seed, freq_lo, freq_hi):
    """tileable band-limited noise in [0,1]"""
    h, w = (size, size) if isinstance(size, int) else size
    r = rng(seed)
    wn = r.standard_normal((h, w))
    fy = np.fft.fftfreq(h)[:, None] * h
    fx = np.fft.fftfreq(w)[None, :] * w
    f = np.sqrt(fx * fx + fy * fy)
    mask = ((f >= freq_lo) & (f <= freq_hi)).astype(float)
    out = np.real(np.fft.ifft2(np.fft.fft2(wn) * mask))
    out -= out.min()
    return out / max(1e-9, out.max())


def lerp(a, b, t):
    return a + (b - a) * t


def rgb(c):
    return np.array([int(c[i:i + 2], 16) for i in (1, 3, 5)], dtype=float) / 255.0


def compose(base, layers):
    """base HxWx3 float; layers: (mask HxW, color 3 or HxWx3, opacity)"""
    out = base.copy()
    for mask, col, op in layers:
        m = np.clip(mask * op, 0, 1)[..., None]
        out = out * (1 - m) + np.asarray(col) * m
    return out


def to_img(a, alpha=None):
    a = np.clip(a, 0, 1)
    if alpha is None:
        return Image.fromarray((a * 255 + 0.5).astype(np.uint8), 'RGB').convert('RGBA')
    al = np.clip(alpha, 0, 1)
    return Image.fromarray((np.dstack([a, al]) * 255 + 0.5).astype(np.uint8), 'RGBA')


def pad_tile(img, size=512):
    """wrap a tileable INNER x INNER image into a size x size tile with gutters"""
    a = np.asarray(img)
    g = (size - a.shape[0]) // 2
    return Image.fromarray(np.pad(a, ((g, g), (g, g), (0, 0)), mode='wrap'))


def pad_clamp(img, size=512):
    a = np.asarray(img.resize((INNER, INNER), Image.LANCZOS))
    g = (size - INNER) // 2
    return Image.fromarray(np.pad(a, ((g, g), (g, g), (0, 0)), mode='edge'))


def scratches(size, seed, count, length=(10, 60), width=1):
    r = rng(seed)
    img = Image.new('L', (size * 3, size * 3), 0)
    d = ImageDraw.Draw(img)
    for _ in range(count):
        x, y = r.uniform(size, 2 * size, 2)
        a = r.uniform(0, math.pi)
        if r.random() < 0.6:
            a = r.normal(0, 0.25) + (0 if r.random() < 0.5 else math.pi / 2)
        L = r.uniform(*length)
        pts = [(x, y)]
        for k in range(4):
            a += r.normal(0, 0.15)
            x += math.cos(a) * L / 4
            y += math.sin(a) * L / 4
            pts.append((x, y))
        d.line(pts, fill=int(r.uniform(120, 255)), width=width)
    a = np.asarray(img, dtype=float) / 255.0
    # fold the 3x3 canvas back into one tile (tileable)
    out = np.zeros((size, size))
    for i in range(3):
        for j in range(3):
            out = np.maximum(out, a[i * size:(i + 1) * size, j * size:(j + 1) * size])
    return out


def spots(size, seed, count, rmin, rmax, soft=0.5):
    r = rng(seed)
    yy, xx = np.mgrid[0:size, 0:size]
    out = np.zeros((size, size))
    for _ in range(count):
        cx, cy = r.uniform(0, size, 2)
        rad = r.uniform(rmin, rmax)
        dx = np.minimum(abs(xx - cx), size - abs(xx - cx))
        dy = np.minimum(abs(yy - cy), size - abs(yy - cy))
        d = np.sqrt(dx * dx + dy * dy) / rad
        out = np.maximum(out, np.clip((1 - d) / soft, 0, 1))
    return out


def seams(size, every_px, width=2, offset=0, vertical=True, horizontal=True):
    m = np.zeros((size, size))
    for k in range(offset, size, every_px):
        for w in range(width):
            if vertical:
                m[:, (k + w) % size] = 1
            if horizontal:
                m[(k + w) % size, :] = 1
    return m


def rivets(size, every_px, spacing, seed, offset=0):
    """small round heads along seam lines: returns (height, mask)"""
    yy, xx = np.mgrid[0:size, 0:size]
    h = np.zeros((size, size))
    for line in range(offset + 6, size, every_px):
        for k in range(spacing // 2, size, spacing):
            for (cx, cy) in ((line, k), (k, line)):
                dx = np.minimum(abs(xx - cx), size - abs(xx - cx))
                dy = np.minimum(abs(yy - cy), size - abs(yy - cy))
                d = np.sqrt(dx * dx + dy * dy)
                h = np.maximum(h, np.clip(1 - d / 3.2, 0, 1))
    return h


def emboss(height, strength=1.0, light=(-1, -1)):
    """fake lighting from a height map (top-left light): >0.5 lit, <0.5 shadow"""
    gy, gx = np.gradient(height)
    l = (gx * light[0] + gy * light[1]) * strength
    return np.clip(0.5 + l, 0, 1)


def streaks(size, seed, density=0.5):
    """vertical dirt streaks (rain run-off), tileable"""
    r = rng(seed)
    n = band((size, size), seed, 1, 40)
    col = band((1, size), seed + 1, 2, 60)[0] if False else None
    v = noise((size, size), seed + 2, beta=2.6, aspect=(8, 1))
    return np.clip((v - (1 - density)) * 3, 0, 1)


# ------------------------------------------------------------------ material tiles
def paint(seed, base='#4f5a37', var=0.10, wear=1.0, panel_px=248, rivet=True):
    s = INNER
    b = rgb(base)
    lo = noise(s, seed, 2.2)
    mid = band(s, seed + 1, 8, 40)
    fine = band(s, seed + 2, 80, 240)
    col = b[None, None, :] * (1 - var / 2 + var * lo[..., None]) * (0.96 + 0.08 * mid[..., None])
    col *= (0.97 + 0.06 * fine[..., None])
    # orange-peel micro texture
    col *= (0.985 + 0.03 * band(s, seed + 3, 150, 248)[..., None])
    # panel seams with shadow + rivets
    height = np.zeros((s, s))
    if panel_px:
        sm = seams(s, panel_px, 2, offset=panel_px // 2)
        height -= sm * 1.0
        if rivet:
            height += rivets(s, panel_px, 22, seed, offset=panel_px // 2) * 0.8
    shade = emboss(np.asarray(Image.fromarray((height * 60 + 128).clip(0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8)), dtype=float) / 60.0, 1.0)
    col *= (0.55 + 0.9 * shade[..., None])
    # dirt, dust, rust and scratches
    dirt = np.clip((noise(s, seed + 4, 2.4) - 0.55) * 2.5, 0, 1) * wear
    dust = np.clip((noise(s, seed + 5, 1.8) - 0.45) * 1.6, 0, 1) * wear
    st = streaks(s, seed + 6, 0.35) * wear
    sc = scratches(s, seed + 7, int(26 * wear), (6, 30))
    rust = spots(s, seed + 8, int(6 * wear), 2, 7, 0.7) * np.clip(noise(s, seed + 9, 2.0) * 1.5, 0, 1)
    col = compose(col, [
        (dust, rgb('#8a8165'), 0.35),
        (dirt, rgb('#3a3426'), 0.45),
        (st, rgb('#2e2a20'), 0.35),
        (sc, rgb('#7d826c'), 0.28),
        (rust, rgb('#6b3a1e'), 0.8),
    ])
    return to_img(col)


def metal(seed, base='#3b3d3f', rough=0.5):
    s = INNER
    b = rgb(base)
    brushed = noise(s, seed, 2.0, aspect=(1, 12))
    lo = noise(s, seed + 1, 2.4)
    col = b[None, None, :] * (0.8 + 0.3 * lo[..., None]) * (0.92 + 0.16 * brushed[..., None])
    oil = np.clip((noise(s, seed + 2, 2.2) - 0.6) * 3, 0, 1)
    sc = scratches(s, seed + 3, 90, (6, 40))
    rust = np.clip((noise(s, seed + 4, 2.6) - 0.7) * 3, 0, 1) * rough
    col = compose(col, [(oil, rgb('#15140f'), 0.6), (sc, rgb('#a7a9aa'), 0.5), (rust, rgb('#5e3417'), 0.7)])
    return to_img(col)


def chrome(seed):
    s = INNER
    y = np.linspace(0, 1, s)[:, None] * np.ones((1, s))
    # reflection bands across the rod (u runs around it)
    x = np.linspace(0, 2 * math.pi, s)[None, :] * np.ones((s, 1))
    refl = 0.55 + 0.35 * np.cos(x) ** 3 + 0.15 * np.cos(3 * x)
    streak = noise(s, seed, 2.2, aspect=(14, 1))
    col = np.dstack([refl * 0.92, refl * 0.94, refl])[..., :3] * (0.9 + 0.15 * streak[..., None])
    oil = np.clip((noise(s, seed + 1, 2.0) - 0.62) * 3, 0, 1)
    col = compose(col, [(oil, rgb('#2a2620'), 0.4)])
    return to_img(np.clip(col, 0, 1))


def tread(seed):
    s = INNER
    yy, xx = np.mgrid[0:s, 0:s]
    # chevron tread blocks (u around the tyre, v across)
    period = s // 8
    v = (yy / s - 0.5)
    phase = ((xx + np.abs(v) * period * 1.2) % period) / period
    groove = ((phase > 0.62) & (np.abs(v) > 0.06)).astype(float)
    center = (np.abs(v) < 0.03).astype(float)
    base = rgb('#1d1d1c')
    col = base[None, None, :] * (0.9 + 0.2 * noise(s, seed, 2.0)[..., None])
    shade = 1 - 0.55 * np.clip(groove + center, 0, 1)
    col *= shade[..., None]
    dust = np.clip((noise(s, seed + 1, 2.0) - 0.4) * 1.5, 0, 1)
    col = compose(col, [(dust * (1 - groove), rgb('#5b5442'), 0.35)])
    return to_img(col)


def sidewall(seed):
    """radially mapped wheel disk: green hub, rim, rubber"""
    s = INNER
    yy, xx = np.mgrid[0:s, 0:s] - s / 2 + 0.5
    rr = np.sqrt(xx * xx + yy * yy) / (s / 2)
    ang = np.arctan2(yy, xx)
    rubber = rgb('#1c1c1b') * (0.9 + 0.1 * noise(s, seed, 2.0)[..., None])
    green = rgb('#4b5634') * (0.85 + 0.25 * noise(s, seed + 1, 2.2)[..., None])
    col = np.where((rr < 0.62)[..., None], green, rubber)
    holes = ((rr > 0.3) & (rr < 0.45) & (np.cos(ang * 8) > 0.75)).astype(float)
    ring = (np.abs(rr - 0.62) < 0.015).astype(float) + (np.abs(rr - 0.22) < 0.012).astype(float)
    col = compose(col, [(holes, rgb('#0b0b0a'), 0.95), (ring, rgb('#2a2e1f'), 0.9)])
    letters = ((rr > 0.75) & (rr < 0.82) & (np.sin(ang * 60) > 0.6) & (np.cos(ang * 3) > 0.3)).astype(float)
    col = compose(col, [(letters, rgb('#2b2b2a'), 0.8)])
    return to_img(col)


def grille(seed):
    s = INNER
    yy, xx = np.mgrid[0:s, 0:s]
    slat = ((yy % 31) < 18).astype(float)
    shade = 0.35 + 0.65 * slat * (1 - ((yy % 31) / 18.0) * 0.5)
    base = rgb('#4a5434')
    col = base[None, None, :] * shade[..., None] * (0.9 + 0.2 * noise(s, seed, 2.0)[..., None])
    return to_img(col)


def glass(seed):
    s = INNER
    y = np.linspace(0, 1, s)[:, None] * np.ones((1, s))
    sky = lerp(rgb('#6f8798'), rgb('#1c252c'), y[..., None] ** 0.7)
    refl = np.clip(noise(s, seed, 2.6, aspect=(1, 3)) * 1.4 - 0.5, 0, 1)
    col = sky * (0.75 + 0.35 * refl[..., None])
    dirt = np.clip((noise(s, seed + 1, 2.2) - 0.55) * 2, 0, 1)
    col = compose(col, [(dirt, rgb('#5a5444'), 0.3)])
    return to_img(col)


def rubber(seed):
    s = INNER
    col = rgb('#1b1b1a')[None, None, :] * (0.85 + 0.25 * noise(s, seed, 2.0)[..., None])
    return to_img(col)


def stripes(seed):
    s = INNER
    yy, xx = np.mgrid[0:s, 0:s]
    st = (((xx + yy) % (s // 4)) < s // 8).astype(float)
    col = np.where(st[..., None] > 0.5, rgb('#d9b21a'), rgb('#161512'))
    wear = np.clip((noise(s, seed, 2.2) - 0.5) * 2.5, 0, 1)
    col = compose(col, [(wear, rgb('#5a5a50'), 0.5), (scratches(s, seed + 1, 50), rgb('#8a8a80'), 0.5)])
    return to_img(col)


def cab_front(seed, w_m=3.4, h_m=0.73):
    """lower front of the cab (stretched later onto a square tile)"""
    W, H = 1700, int(1700 * h_m / w_m)
    base = np.asarray(paint(seed, '#4f5a37', panel_px=0).resize((W, W)))[:H, :, :3] / 255.0
    img = to_img(base)
    d = ImageDraw.Draw(img)
    # central grille
    gx0, gx1 = int(W * 0.30), int(W * 0.70)
    for k in range(7):
        yy = int(H * 0.15 + k * H * 0.1)
        d.rectangle([gx0, yy, gx1, yy + int(H * 0.05)], fill=(30, 33, 22, 255))
    d.rectangle([gx0 - 8, int(H * 0.12), gx1 + 8, int(H * 0.86)], outline=(70, 78, 52, 255), width=6)
    # headlight housings
    for cx in (0.14, 0.86):
        x0 = int(W * cx - W * 0.07)
        d.rounded_rectangle([x0, int(H * 0.25), x0 + int(W * 0.14), int(H * 0.8)], 12, fill=(36, 40, 28, 255), outline=(90, 96, 70, 255), width=5)
    # tow hooks and number plate
    for cx in (0.25, 0.75):
        d.rectangle([int(W * cx) - 18, int(H * 0.88), int(W * cx) + 18, H], fill=(25, 25, 22, 255))
    font = ImageFont.truetype(FONT_MONO, int(H * 0.16))
    d.rectangle([int(W * 0.42), int(H * 0.88) - 10, int(W * 0.58), H - 2], fill=(18, 18, 18, 255))
    d.text((int(W * 0.432), int(H * 0.875) - 8), '12-34 ВЕ', fill=(220, 220, 220, 255), font=font)
    return img


def text_decal(lines, w_px, h_px, color=(232, 232, 220, 255), font=FONT_BOLD, size=None, bg=None, frame=False):
    img = Image.new('RGBA', (w_px, h_px), bg or (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    fs = size or int(h_px / (len(lines) + 0.6))
    f = ImageFont.truetype(font, fs)
    y = (h_px - fs * len(lines) * 1.1) / 2
    for line in lines:
        tw = d.textlength(line, font=f)
        d.text(((w_px - tw) / 2, y), line, fill=color, font=f)
        y += fs * 1.1
    if frame:
        d.rectangle([3, 3, w_px - 4, h_px - 4], outline=color, width=max(2, h_px // 40))
    # stencil wear
    a = np.asarray(img).astype(float)
    wear = noise((h_px, w_px), 77, 2.0)
    a[..., 3] *= np.clip(0.55 + wear * 0.8, 0, 1)
    return Image.fromarray(a.clip(0, 255).astype(np.uint8))


def lights_tile():
    s = 512
    img = Image.new('RGBA', (s, s), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    yy, xx = np.mgrid[0:int(s * 0.3), 0:int(s * 0.5)]
    # headlight lens (sub 0,0 - 0.5,0.3)
    cx, cy = s * 0.25, s * 0.15
    r = np.sqrt(((xx - cx) / (s * 0.25)) ** 2 + ((yy - cy) / (s * 0.15)) ** 2)
    lens = np.clip(1.2 - r, 0, 1)
    a = np.zeros((int(s * 0.3), int(s * 0.5), 3))
    a[..., 0] = 0.55 + 0.45 * lens
    a[..., 1] = 0.52 + 0.45 * lens
    a[..., 2] = 0.42 + 0.4 * lens
    img.paste(Image.fromarray((a * 255).astype(np.uint8)), (0, 0))
    d.rectangle([s // 2, 0, s, int(s * 0.15)], fill=(255, 150, 30, 255))   # amber marker
    for k in range(64):
        rr = 1 - k / 64
        c = (int(255 * min(1, 0.5 + rr)), int(90 * rr + 40), int(30 * rr + 10), 255)
        d.ellipse([s * 0.75 - rr * s * 0.25, s * 0.75 - rr * s * 0.25, s * 0.75 + rr * s * 0.25, s * 0.75 + rr * s * 0.25], fill=c)
    return img


def cap_tile(seed):
    s = INNER
    img = np.asarray(paint(seed, '#4a5533', panel_px=124, rivet=True), dtype=float) / 255.0
    yy, xx = np.mgrid[0:s, 0:s]
    bandm = ((yy % 124) < 6).astype(float)
    img[..., :3] = compose(img[..., :3], [(bandm, rgb('#2b301f'), 0.8)])
    return to_img(img[..., :3])


# ------------------------------------------------------------------ missile
def missile_paint(seed, base):
    return paint(seed, base, var=0.06, wear=0.35, panel_px=248, rivet=True)


def nozzle_inner(seed):
    s = INNER
    y = np.linspace(0, 1, s)[:, None] * np.ones((1, s))
    soot = rgb('#0e0d0c')
    heat = lerp(rgb('#3a2c22'), rgb('#7a5a3c'), np.clip(noise(s, seed, 2.0), 0, 1)[..., None])
    col = lerp(heat, soot[None, None, :] * np.ones((s, s, 3)), np.clip(y[..., None] * 1.2, 0, 1))
    rings = (np.sin(y * 60) > 0.92).astype(float)
    col = compose(col, [(rings, rgb('#050505'), 0.4)])
    return to_img(col)


def nozzle_outer(seed):
    s = INNER
    y = np.linspace(0, 1, s)[:, None] * np.ones((1, s))
    base = rgb('#1f1e1d') * (0.8 + 0.4 * noise(s, seed, 2.0)[..., None])
    weave = (band(s, seed + 1, 120, 200) > 0.55).astype(float)
    col = compose(base, [(weave, rgb('#2c2b2a'), 0.4), ((np.sin(y * 50) > 0.95).astype(float), rgb('#3d3b38'), 0.6)])
    heat = np.clip(1 - y * 3, 0, 1)
    col = compose(col, [(heat, rgb('#54402d'), 0.4)])
    return to_img(col)


def grid_metal(seed):
    return metal(seed, '#6c6f70', rough=0.1)


def insulation(seed):
    s = INNER
    col = rgb('#b59a5a')[None, None, :] * (0.85 + 0.25 * noise(s, seed, 2.0)[..., None])
    quilt = seams(s, 62, 2)
    col = compose(col, [(quilt, rgb('#7a6638'), 0.7)])
    return to_img(col)


def heatshield(seed):
    s = INNER
    lo = noise(s, seed, 2.3)
    pits = spots(s, seed + 1, 160, 1.5, 5.0, 0.6)
    col = lerp(rgb('#1a1410'), rgb('#4a3424'), lo[..., None])
    col = compose(col, [(pits, rgb('#0a0807'), 0.7), (np.clip((noise(s, seed + 2, 2.6) - 0.6) * 3, 0, 1), rgb('#6e5236'), 0.5)])
    return to_img(col)


def band_tile(seed):
    s = INNER
    img = np.asarray(metal(seed, '#5d6162', 0.2), dtype=float)[..., :3] / 255.0
    bolts = rivets(s, 124, 31, seed, 0) 
    img = compose(img, [(np.clip(bolts * 2, 0, 1), rgb('#8d9192'), 0.8)])
    return to_img(img)
