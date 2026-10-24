"""Tiny mesh builder for the Topol mod: primitives with tiled atlas UVs, per-vertex shade, TMSH writer.

Model space: metres, +Y up, +Z forward (front of the vehicle), +X left. Every primitive maps its surface into a
material tile of the atlas; large faces are split into cells of at most `tile_m` metres so one tile repeats
without UV wrapping (the atlas has no wrap mode).
"""
import math
import struct

import numpy as np

ATLAS_TILES = 4          # 4x4 tiles per atlas
TILE_PX = 512
GUTTER_PX = 8            # wrapped border around every tile, UVs stay inside


class Material:
    def __init__(self, name, col, row, tile_m=2.0, shade=1.0, emissive=False, unique=False):
        self.name, self.col, self.row, self.tile_m = name, col, row, tile_m
        self.shade, self.emissive, self.unique = shade, emissive, unique

    def uv(self, fu, fv):
        """fu, fv in [0,1] inside the tile -> atlas UV (inside the gutter)."""
        size = ATLAS_TILES * TILE_PX
        inner = TILE_PX - 2 * GUTTER_PX
        u = (self.col * TILE_PX + GUTTER_PX + fu * inner) / size
        v = (self.row * TILE_PX + GUTTER_PX + fv * inner) / size
        return u, v

    def sub(self, u0, v0, u1, v1):
        """unique sub-rectangle of the tile (decals): returns a material-like mapper"""
        return SubRect(self, u0, v0, u1, v1)


class SubRect:
    def __init__(self, mat, u0, v0, u1, v1):
        self.mat, self.r = mat, (u0, v0, u1, v1)
        self.name, self.tile_m, self.shade, self.emissive, self.unique = mat.name, 1e9, mat.shade, mat.emissive, True

    def uv(self, fu, fv):
        u0, v0, u1, v1 = self.r
        return self.mat.uv(u0 + (u1 - u0) * fu, v0 + (v1 - v0) * fv)


class Part:
    def __init__(self, name, pivot=(0, 0, 0)):
        self.name = name
        self.pivot = tuple(float(c) for c in pivot)
        self.pos, self.nrm, self.uv, self.col, self.tris = [], [], [], [], []
        self.emissive = False

    def vert(self, p, n, uv, shade=1.0):
        self.pos.append(tuple(float(c) for c in p))
        nn = np.asarray(n, dtype=float)
        ln = np.linalg.norm(nn)
        nn = nn / ln if ln > 1e-9 else np.array([0.0, 1.0, 0.0])
        self.nrm.append(tuple(nn))
        self.uv.append((float(uv[0]), float(uv[1])))
        self.col.append(max(0.0, min(1.0, shade)))
        return len(self.pos) - 1

    def tri(self, a, b, c):
        self.tris.append((a, b, c, c))

    def quad(self, a, b, c, d):
        self.tris.append((a, b, c, d))

    def transform(self, m, start=0):
        """apply a 4x4 matrix to vertices [start:] (normals by the inverse transpose)"""
        m = np.asarray(m, dtype=float)
        nm = np.linalg.inv(m[:3, :3]).T
        for i in range(start, len(self.pos)):
            p = m @ np.array([*self.pos[i], 1.0])
            self.pos[i] = tuple(p[:3])
            n = nm @ np.array(self.nrm[i])
            n /= max(1e-9, np.linalg.norm(n))
            self.nrm[i] = tuple(n)

    def merge(self, other):
        base = len(self.pos)
        self.pos += other.pos
        self.nrm += other.nrm
        self.uv += other.uv
        self.col += other.col
        self.tris += [tuple(i + base for i in q) for q in other.tris]

    def bounds(self):
        a = np.array(self.pos)
        return a.min(axis=0), a.max(axis=0)


# ------------------------------------------------------------------ matrices

def translate(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def scale(x, y, z):
    return np.diag([x, y, z, 1.0])


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1.0]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1.0]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1.0]])


def align_y_to(d):
    """rotation taking +Y onto direction d"""
    d = np.asarray(d, dtype=float)
    d = d / np.linalg.norm(d)
    y = np.array([0.0, 1.0, 0.0])
    v = np.cross(y, d)
    c = float(np.dot(y, d))
    if np.linalg.norm(v) < 1e-9:
        return np.eye(4) if c > 0 else rot_x(math.pi)
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    r = np.eye(3) + vx + vx @ vx * (1 / (1 + c))
    m = np.eye(4)
    m[:3, :3] = r
    return m


# ------------------------------------------------------------------ primitives

def _cuts(length, tile):
    """cut positions 0..length at every multiple of the tile (seamless with tileable textures)"""
    cuts = [0.0]
    k = 1
    while k * tile < length - 1e-6:
        cuts.append(k * tile)
        k += 1
    cuts.append(length)
    return cuts


def face(part, origin, eu, ev, lu, lv, mat, shade=1.0, flip=False):
    """planar rectangle origin + eu*s + ev*t (eu, ev unit vectors, lu/lv lengths). The front side is eu x ev
    (counter-clockwise seen from it). Tiled materials repeat every tile_m metres, unique ones span the face."""
    origin, eu, ev = (np.asarray(a, dtype=float) for a in (origin, eu, ev))
    n = np.cross(eu, ev)
    if flip:
        n = -n
    unique = getattr(mat, 'unique', False)
    tile = mat.tile_m
    us = [0.0, lu] if unique else _cuts(lu, tile)
    vs = [0.0, lv] if unique else _cuts(lv, tile)
    for i in range(len(us) - 1):
        for j in range(len(vs) - 1):
            corners = []
            for (a, b, ia, jb) in ((us[i], vs[j], i, j), (us[i + 1], vs[j], i + 1, j), (us[i + 1], vs[j + 1], i + 1, j + 1), (us[i], vs[j + 1], i, j + 1)):
                p = origin + eu * a + ev * b
                if unique:
                    fu, fv = a / lu, 1 - b / lv
                else:
                    fu = (a - us[i]) / tile
                    fv = 1 - (b - vs[j]) / tile
                corners.append(part.vert(p, n, mat.uv(min(1, max(0, fu)), min(1, max(0, fv))), shade * mat.shade))
            a0, a1, a2, a3 = corners
            if flip:
                part.quad(a0, a3, a2, a1)
            else:
                part.quad(a0, a1, a2, a3)


def box(part, lo, hi, mat, shade=1.0, faces='xXyYzZ', mats=None):
    """axis-aligned box lo..hi; faces string picks sides (x=-X, X=+X ...). mats overrides per face."""
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
    m = lambda k: (mats or {}).get(k, mat)
    sh_side, sh_top, sh_bot = shade, shade * 1.0, shade * 0.62
    if 'X' in faces:
        face(part, (x1, y0, z1), (0, 0, -1), (0, 1, 0), dz, dy, m('X'), sh_side)
    if 'x' in faces:
        face(part, (x0, y0, z0), (0, 0, 1), (0, 1, 0), dz, dy, m('x'), sh_side)
    if 'Z' in faces:
        face(part, (x0, y0, z1), (1, 0, 0), (0, 1, 0), dx, dy, m('Z'), sh_side)
    if 'z' in faces:
        face(part, (x1, y0, z0), (-1, 0, 0), (0, 1, 0), dx, dy, m('z'), sh_side)
    if 'Y' in faces:
        face(part, (x0, y1, z1), (1, 0, 0), (0, 0, -1), dx, dz, m('Y'), sh_top)
    if 'y' in faces:
        face(part, (x0, y0, z0), (1, 0, 0), (0, 0, 1), dx, dz, m('y'), sh_bot)


def lathe(part, profile, sides, mat, m=None, shade=1.0, arc=(0.0, 2 * math.pi), cap_mat=None, smooth=True):
    """surface of revolution around +Y. profile: [(r, y), ...] from bottom to top.
    UV: u around (arc length / tile), v along the profile length; cells limited to the tile size."""
    start = len(part.pos)
    tile = mat.tile_m
    # cut the profile at every tile multiple of its running length (seamless along the axis)
    pts, vcoord = [profile[0]], [0.0]
    run = 0.0
    for (r0, y0), (r1, y1) in zip(profile, profile[1:]):
        L = math.hypot(r1 - r0, y1 - y0)
        if L < 1e-9:
            continue
        cuts = [c for c in _cuts(run + L, tile) if c > run + 1e-6]
        for c in cuts:
            t = (c - run) / L
            pts.append((r0 + (r1 - r0) * t, y0 + (y1 - y0) * t))
            vcoord.append(c)
        run += L
    rmax = max(r for r, _ in pts)
    circ = max(1e-6, rmax * (arc[1] - arc[0]))
    ncell_u = max(1, int(round(circ / tile)))
    sides = int(math.ceil(max(sides, ncell_u) / ncell_u) * ncell_u)
    per_cell = sides // ncell_u
    prof_n = []
    for i, (r, y) in enumerate(pts):
        a = pts[max(0, i - 1)]
        b = pts[min(len(pts) - 1, i + 1)]
        tr, ty = b[0] - a[0], b[1] - a[1]
        ln = math.hypot(tr, ty) or 1.0
        prof_n.append((ty / ln, -tr / ln))
    for j in range(len(pts) - 1):
        (r0, y0), (r1, y1) = pts[j], pts[j + 1]
        seg = math.hypot(r1 - r0, y1 - y0) or 1.0
        base_v = math.floor(vcoord[j] / tile + 1e-9) * tile
        fv0 = 1.0 - (vcoord[j] - base_v) / tile
        fv1 = 1.0 - (vcoord[j + 1] - base_v) / tile
        for k in range(sides):
            cell = k // per_cell
            fu0 = (k - cell * per_cell) / per_cell
            fu1 = (k + 1 - cell * per_cell) / per_cell
            a0 = arc[0] + (arc[1] - arc[0]) * k / sides
            a1 = arc[0] + (arc[1] - arc[0]) * (k + 1) / sides
            quadv = []
            for (aa, rr, yy, fu, fv, pn) in ((a0, r0, y0, fu0, fv0, prof_n[j]), (a1, r0, y0, fu1, fv0, prof_n[j]),
                                             (a1, r1, y1, fu1, fv1, prof_n[j + 1]), (a0, r1, y1, fu0, fv1, prof_n[j + 1])):
                if not smooth:
                    pn = ((y1 - y0) / seg, -(r1 - r0) / seg)
                ca, sa = math.cos(aa), math.sin(aa)
                p = (rr * ca, yy, -rr * sa)
                n = (pn[0] * ca, pn[1], -pn[0] * sa)
                quadv.append(part.vert(p, n, mat.uv(min(1, max(0, fu)), min(1, max(0, fv))), shade * mat.shade))
            part.quad(*quadv)
    if cap_mat is not None:
        for (r, y, up) in ((pts[0][0], pts[0][1], False), (pts[-1][0], pts[-1][1], True)):
            if r < 1e-4:
                continue
            c = part.vert((0, y, 0), (0, 1 if up else -1, 0), cap_mat.uv(0.5, 0.5), shade * cap_mat.shade)
            ring = []
            for k in range(sides + 1):
                aa = arc[0] + (arc[1] - arc[0]) * k / sides
                ca, sa = math.cos(aa), math.sin(aa)
                ring.append(part.vert((r * ca, y, -r * sa), (0, 1 if up else -1, 0),
                                      cap_mat.uv(0.5 + 0.5 * ca * min(1, r / cap_mat.tile_m * 2), 0.5 - 0.5 * sa * min(1, r / cap_mat.tile_m * 2)),
                                      shade * cap_mat.shade))
            for k in range(sides):
                if up:
                    part.tri(c, ring[k], ring[k + 1])
                else:
                    part.tri(c, ring[k + 1], ring[k])
    if m is not None:
        part.transform(m, start)


def cylinder(part, p0, p1, r, sides, mat, caps=True, cap_mat=None, shade=1.0, r1=None):
    """cylinder (or frustum with r1) from p0 to p1"""
    p0, p1 = np.asarray(p0, dtype=float), np.asarray(p1, dtype=float)
    d = p1 - p0
    L = float(np.linalg.norm(d))
    rt = r if r1 is None else r1
    start = len(part.pos)
    lathe(part, [(r, 0.0), (rt, L)], sides, mat, cap_mat=(cap_mat or mat) if caps else None, shade=shade, smooth=True)
    part.transform(translate(*p0) @ align_y_to(d), start)


def write_tmsh(path, parts, anchors):
    """TMSH v1: magic, version, nparts, [name, pivot, emissive, nverts, pos, nrm, uv, shade, nquads, idx*4], nanchors, [name, xyz].
    Triangles are stored as quads with the last corner repeated (entity layers draw quads)."""
    with open(path, 'wb') as f:
        f.write(struct.pack('>ii', 0x544D5348, 1))
        f.write(struct.pack('>i', len(parts)))
        for p in parts:
            name = p.name.encode('utf-8')
            f.write(struct.pack('>H', len(name)) + name)
            f.write(struct.pack('>fff', *p.pivot))
            f.write(struct.pack('>?', p.emissive))
            n = len(p.pos)
            f.write(struct.pack('>i', n))
            f.write(np.asarray(p.pos, dtype='>f4').tobytes())
            f.write(np.asarray(p.nrm, dtype='>f4').tobytes())
            f.write(np.asarray(p.uv, dtype='>f4').tobytes())
            f.write(np.asarray(np.round(np.asarray(p.col) * 255), dtype='u1').tobytes())
            f.write(struct.pack('>i', len(p.tris)))
            f.write(np.asarray(p.tris, dtype='>i4').tobytes())
        f.write(struct.pack('>i', len(anchors)))
        for k, v in anchors.items():
            name = k.encode('utf-8')
            f.write(struct.pack('>H', len(name)) + name)
            f.write(struct.pack('>fff', *v))
