"""Small z-buffer rasterizer for honest previews: Minecraft-style entity lighting
(two directional lights * 0.6 + 0.4 ambient), bilinear textures (mcmeta blur), per-vertex AO tint."""
import math
import numpy as np
from PIL import Image

L0 = np.array([0.2, 1.0, -0.7]); L0 /= np.linalg.norm(L0)
L1 = np.array([-0.2, 1.0, 0.7]); L1 /= np.linalg.norm(L1)


def look_at(eye, target, up=(0, 1, 0)):
    eye, target, up = (np.asarray(a, float) for a in (eye, target, up))
    f = target - eye; f /= np.linalg.norm(f)
    r = np.cross(f, up); r /= np.linalg.norm(r)
    u = np.cross(r, f)
    m = np.eye(4)
    m[0, :3], m[1, :3], m[2, :3] = r, u, -f
    m[:3, 3] = -m[:3, :3] @ eye
    return m


class Mesh:
    """triangle soup: pos (n,3), nrm (n,3), uv (n,2), col (n,3), tri (k,3); tex HxWx3 float or None; flags"""
    def __init__(self, pos, nrm, uv, col, tri, tex=None, emissive=False, nearest=False, lit=True):
        self.pos, self.nrm, self.uv, self.col = (np.asarray(a, float) for a in (pos, nrm, uv, col))
        self.tri = np.asarray(tri, np.int64)
        self.tex = None if tex is None else (np.asarray(tex, np.float32) / (255.0 if np.asarray(tex).dtype == np.uint8 else 1.0))
        self.emissive, self.nearest, self.lit = emissive, nearest, lit


def sample(tex, uv, nearest=False):
    h, w = tex.shape[:2]
    u = (uv[:, 0] % 1.0) * w - 0.5
    v = (uv[:, 1] % 1.0) * h - 0.5
    if nearest:
        x = np.clip(np.floor(u + 0.5).astype(int), 0, w - 1); y = np.clip(np.floor(v + 0.5).astype(int), 0, h - 1)
        return tex[y, x, :3]
    x0 = np.floor(u).astype(int); y0 = np.floor(v).astype(int)
    fx = (u - x0)[:, None]; fy = (v - y0)[:, None]
    x0 %= w; y0 %= h; x1 = (x0 + 1) % w; y1 = (y0 + 1) % h
    a = tex[y0, x0, :3] * (1 - fx) + tex[y0, x1, :3] * fx
    b = tex[y1, x0, :3] * (1 - fx) + tex[y1, x1, :3] * fx
    return a * (1 - fy) + b * fy


def render(meshes, eye, target, size=(1200, 800), fov=40, ss=2, sky=((0.47, 0.65, 1.0), (0.75, 0.85, 1.0)), fog=None, up=(0, 1, 0), raw=False):
    W, H = size[0] * ss, size[1] * ss
    view = look_at(eye, target, up)
    f = 1.0 / math.tan(math.radians(fov) / 2)
    zbuf = np.full((H, W), np.inf)
    mid = np.full((H, W), -1, np.int32); tid = np.full((H, W), -1, np.int64)
    bary = np.zeros((H, W, 3), np.float32)
    proj = []
    for mi, m in enumerate(meshes):
        pc = (np.c_[m.pos, np.ones(len(m.pos))] @ view.T)[:, :3]
        z = -pc[:, 2]
        sx = (pc[:, 0] * f / np.maximum(z, 1e-6) * (H / 2)) + W / 2
        sy = (-pc[:, 1] * f / np.maximum(z, 1e-6) * (H / 2)) + H / 2
        proj.append((sx, sy, z))
        for ti, (a, b, c) in enumerate(m.tri):
            za, zb, zc = z[a], z[b], z[c]
            if min(za, zb, zc) < 0.05:
                continue
            xa, xb, xc, ya, yb, yc = sx[a], sx[b], sx[c], sy[a], sy[b], sy[c]
            area = (xb - xa) * (yc - ya) - (xc - xa) * (yb - ya)
            if abs(area) < 1e-9:
                continue
            x0 = max(int(math.floor(min(xa, xb, xc))), 0); x1 = min(int(math.ceil(max(xa, xb, xc))), W - 1)
            y0 = max(int(math.floor(min(ya, yb, yc))), 0); y1 = min(int(math.ceil(max(ya, yb, yc))), H - 1)
            if x0 > x1 or y0 > y1:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            w0 = ((xb - gx) * (yc - gy) - (xc - gx) * (yb - gy)) / area
            w1 = ((xc - gx) * (ya - gy) - (xa - gx) * (yc - gy)) / area
            w2 = 1 - w0 - w1
            ins = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not ins.any():
                continue
            iz = w0 / za + w1 / zb + w2 / zc
            zz = 1.0 / np.maximum(iz, 1e-12)
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            upd = ins & (zz < sub)
            if not upd.any():
                continue
            sub[upd] = zz[upd]
            mid[y0:y1 + 1, x0:x1 + 1][upd] = mi
            tid[y0:y1 + 1, x0:x1 + 1][upd] = ti
            pb = np.stack([w0 / za, w1 / zb, w2 / zc], -1) * zz[..., None]
            bsub = bary[y0:y1 + 1, x0:x1 + 1]
            bsub[upd] = pb[upd]
    # background sky gradient
    t = np.linspace(0, 1, H)[:, None, None]
    img = np.asarray(sky[0])[None, None, :] * (1 - t) + np.asarray(sky[1])[None, None, :] * t
    img = np.broadcast_to(img, (H, W, 3)).copy()
    for mi, m in enumerate(meshes):
        sel = mid == mi
        if not sel.any():
            continue
        tri = m.tri[tid[sel]]
        b = bary[sel]
        def interp(attr):
            return attr[tri[:, 0]] * b[:, 0:1] + attr[tri[:, 1]] * b[:, 1:2] + attr[tri[:, 2]] * b[:, 2:3]
        col = interp(m.col)
        if m.tex is not None:
            col = col * sample(m.tex, interp(m.uv), m.nearest)
        if m.lit and not m.emissive:
            n = interp(m.nrm)
            n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-9)
            # two-sided like cull-less entity layers: face the viewer
            pos = interp(m.pos)
            vdir = np.asarray(eye, float)[None, :] - pos
            flip = (n * vdir).sum(1) < 0
            n[flip] *= -1
            lam = np.minimum(1.0, (np.maximum(0, n @ L0) + np.maximum(0, n @ L1)) * 0.6 + 0.4)
            col = col * lam[:, None]
        if fog is not None:
            d = zbuf[sel][:, None]
            k = np.clip((d - fog[0]) / (fog[1] - fog[0]), 0, 1)
            col = col * (1 - k) + np.asarray(sky[1])[None, :] * k
        img[sel] = col
    if raw:
        return img, zbuf, dict(view=view, f=f, W=W, H=H, eye=np.asarray(eye, float), ss=ss, size=size)
    im = Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8))
    return im.resize(size, Image.LANCZOS) if ss > 1 else im


def depth_only(pos, tri, view_dir, res=512, center=None, extent=None):
    """orthographic depth map along view_dir; returns (depth, project(points)->(px,py,depth))"""
    d = np.asarray(view_dir, float); d /= np.linalg.norm(d)
    up = np.array([0, 1, 0.0]) if abs(d[1]) < 0.9 else np.array([1, 0, 0.0])
    r = np.cross(d, up); r /= np.linalg.norm(r); u = np.cross(r, d)
    c = pos.mean(0) if center is None else center
    q = pos - c
    px, py, pz = q @ r, q @ u, q @ d
    ext = extent or max(np.abs(px).max(), np.abs(py).max()) * 1.02
    sx = (px / ext * 0.5 + 0.5) * res; sy = (py / ext * 0.5 + 0.5) * res
    zb = np.full((res, res), np.inf)
    for a, b_, cc in tri:
        xa, xb, xc, ya, yb, yc = sx[a], sx[b_], sx[cc], sy[a], sy[b_], sy[cc]
        area = (xb - xa) * (yc - ya) - (xc - xa) * (yb - ya)
        if abs(area) < 1e-12:
            continue
        x0 = max(int(math.floor(min(xa, xb, xc))), 0); x1 = min(int(math.ceil(max(xa, xb, xc))), res - 1)
        y0 = max(int(math.floor(min(ya, yb, yc))), 0); y1 = min(int(math.ceil(max(ya, yb, yc))), res - 1)
        if x0 > x1 or y0 > y1:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        w0 = ((xb - gx) * (yc - gy) - (xc - gx) * (yb - gy)) / area
        w1 = ((xc - gx) * (ya - gy) - (xa - gx) * (yc - gy)) / area
        w2 = 1 - w0 - w1
        ins = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        zz = w0 * pz[a] + w1 * pz[b_] + w2 * pz[cc]
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        upd = ins & (zz < sub)
        sub[upd] = zz[upd]
    def project(pts):
        qq = pts - c
        return (qq @ r / ext * 0.5 + 0.5) * res, (qq @ u / ext * 0.5 + 0.5) * res, qq @ d
    return zb, project, ext


def bake_ao(pos, nrm, occ_pos, occ_tri, dirs=64, res=384, bias=None, upper=False, seed=3):
    """per-vertex ambient occlusion from many orthographic depth maps of the occluders (shadow-map AO).
    pos/nrm: receiving vertices; occ_pos/occ_tri: occluding geometry (normally includes the receivers)."""
    rng = np.random.default_rng(seed)
    v = rng.normal(size=(dirs * 4, 3)); v /= np.linalg.norm(v, axis=1, keepdims=True)
    if upper:
        v = v[v[:, 1] < 0.1]          # light from above: view directions point downward
    v = v[:dirs]
    span = np.ptp(occ_pos, axis=0).max()
    bias = bias or span / res * 2.0
    vis = np.zeros(len(pos)); wsum = np.zeros(len(pos))
    center = (occ_pos.min(0) + occ_pos.max(0)) / 2
    ext = np.linalg.norm(np.ptp(occ_pos, axis=0)) / 2 * 1.02
    for d in v:
        zb, project, _ = depth_only(occ_pos, occ_tri, d, res, center=center, extent=ext)
        px, py, pz = project(pos + nrm * bias)
        ix = np.clip(px.astype(int), 0, res - 1); iy = np.clip(py.astype(int), 0, res - 1)
        lit = pz <= zb[iy, ix] + bias
        w = np.maximum(0, -(nrm @ d))
        vis += lit * w; wsum += w
    return np.where(wsum > 1e-6, vis / np.maximum(wsum, 1e-6), 1.0)
