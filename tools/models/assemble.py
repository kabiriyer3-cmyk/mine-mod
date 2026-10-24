"""Final mod-space parts with tints, triplanar UVs, baked AO. Shared by the preview and the TMSH export."""
import numpy as np
from parts import tomahawk, silo, designator, P
from raster import bake_ao

TINT = {  # silo materials -> military palette (linear-ish 0..1)
    'White': (0.36, 0.38, 0.33), 'YellowedWhite': (0.33, 0.35, 0.30), 'material_0': (0.30, 0.31, 0.31),
    'Blue': (0.22, 0.24, 0.21), 'ShipGrey': (0.33, 0.37, 0.25), 'HighGrip': (1.0, 1.0, 1.0),
    'Grey': (0.4, 0.4, 0.4), 'Silver.001': (0.6, 0.6, 0.6), 'GreyHighlight.001': (0.45, 0.45, 0.45),
}
HINGE_TINT = (0.43, 0.44, 0.44)


def triplanar_uv(pos, nrm, tile=1.0):
    a = np.abs(nrm)
    ax = np.argmax(a, axis=1)
    uv = np.zeros((len(pos), 2))
    m = ax == 0; uv[m] = pos[m][:, [2, 1]] / tile
    m = ax == 1; uv[m] = pos[m][:, [0, 2]] / tile
    m = ax == 2; uv[m] = pos[m][:, [0, 1]] / tile
    return uv


def box_part(name, lo, hi, tex, tile=1.0, faces='xXyYzZ'):
    """plain box (the only hand-built geometry: the concrete collar)"""
    x0, y0, z0 = lo; x1, y1, z1 = hi
    F = {'X': ((x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (1, 0, 0)),
         'x': ((x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0), (-1, 0, 0)),
         'Y': ((x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0), (0, 1, 0)),
         'y': ((x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1), (0, -1, 0)),
         'Z': ((x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1), (0, 0, 1)),
         'z': ((x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (0, 0, -1))}
    pos, nrm, tri = [], [], []
    for k in faces:
        a, b, c, d, n = F[k]
        i = len(pos)
        pos += [a, b, c, d]; nrm += [n] * 4
        tri += [(i, i + 1, i + 2), (i, i + 2, i + 3)]
    pos = np.array(pos, float); nrm = np.array(nrm, float)
    p = P(name, pos, nrm, triplanar_uv(pos, nrm, tile), np.array(tri), None)
    p.tex = tex
    return p


def concrete_pad(outer=(-1.3, 1.3, -1.35, 1.6), hole=0.515, y0=-0.35, y1=0.22, bevel=0.07):
    """concrete headworks slab around the canister mouth: frame with a bevelled outer edge"""
    x0, x1, z0, z1 = outer
    parts = []
    # four slabs around the hole
    for lo, hi in (((x0, y0, z0), (x1, y1, -hole)), ((x0, y0, hole), (x1, y1, z1)),
                   ((x0, y0, -hole), (-hole, y1, hole)), ((hole, y0, -hole), (x1, y1, hole))):
        parts.append(box_part('s', lo, hi, 'concrete', 1.6, faces='Y'))
    # inner walls of the hole
    parts.append(box_part('w', (-hole, y0, -hole), (hole, y1, hole), 'concrete', 1.6, faces='xXzZ'))
    for q in parts[-1:]:
        q.nrm = -q.nrm
        q.tri = q.tri[:, ::-1]
    # bevelled outer rim: vertical sides up to y1 - bevel, then a 45 degree strip
    yb = y1 - bevel
    pos, nrm, tri = [], [], []
    ring = [(x0, z0), (x1, z0), (x1, z1), (x0, z1)]
    for i in range(4):
        (ax, az), (bx, bz) = ring[i], ring[(i + 1) % 4]
        ex, ez = bx - ax, bz - az
        L = (ex * ex + ez * ez) ** 0.5
        nx, nz = ez / L, -ex / L
        ix, iz = -nx * bevel, -nz * bevel
        def inset(px, pz):
            # move a corner inward along both adjacent edges' normals
            return px + (-bevel if px == x1 else bevel if px == x0 else 0), pz + (-bevel if pz == z1 else bevel if pz == z0 else 0)
        for quad, n in ((((ax, y0, az), (bx, y0, bz), (bx, yb, bz), (ax, yb, az)), (nx, 0, nz)),
                        (((ax, yb, az), (bx, yb, bz), (inset(bx, bz)[0], y1, inset(bx, bz)[1]), (inset(ax, az)[0], y1, inset(ax, az)[1])), (nx * 0.707, 0.707, nz * 0.707))):
            k = len(pos)
            pos += list(quad); nrm += [n] * 4
            tri += [(k, k + 2, k + 1), (k, k + 3, k + 2)]
    pos = np.array(pos, float); nrm = np.array(nrm, float)
    rim = P('rim', pos, nrm, triplanar_uv(pos, nrm, 1.6), np.array(tri), None)
    parts.append(rim)
    # fix the top slabs so they stop at the bevel
    allp = P.merge('pad', [dict(pos=q.pos, nrm=q.nrm, uv=q.uv, tri=q.tri, material=None) for q in parts])
    allp.pos[:, 0] = np.clip(allp.pos[:, 0], x0 + bevel * (allp.pos[:, 1] > yb + 1e-6) * 0 , x1)
    top = allp.pos[:, 1] > y1 - 1e-6
    allp.pos[top, 0] = np.clip(allp.pos[top, 0], x0 + bevel, x1 - bevel)
    allp.pos[top, 2] = np.clip(allp.pos[top, 2], z0 + bevel, z1 - bevel)
    allp.uv = triplanar_uv(allp.pos, allp.nrm, 1.6)
    allp.tex = 'concrete'
    # split: the flat top gets the unique decal texture (top-down mapping), the rest tiles concrete
    top_face = (allp.nrm[:, 1] > 0.99)
    tri_top = top_face[allp.tri].all(1)
    def sub(mask_tri, name, tex):
        used = allp.tri[mask_tri]
        uniq, inv = np.unique(used, return_inverse=True)
        q = P(name, allp.pos[uniq].copy(), allp.nrm[uniq].copy(), allp.uv[uniq].copy(), inv.reshape(-1, 3), None)
        q.tex = tex
        return q
    top = sub(tri_top, 'pad_top', 'pad')
    top.uv = np.c_[(top.pos[:, 0] - x0) / (x1 - x0), (top.pos[:, 2] - z0) / (z1 - z0)]
    side = sub(~tri_top, 'pad_side', 'concrete')
    return top, side


def build_silo(ao=True):
    parts, mats = silo()
    out = {}
    for k, p in parts.items():
        mname = mats[p.mat]['name']
        tint = np.array(TINT.get(mname, (0.4, 0.4, 0.4)))
        if k.startswith('hinges'):
            tint = np.array(HINGE_TINT)
        p.tint = tint
        p.tex = 'grip' if mname == 'HighGrip' else 'steel'
        p.uv = triplanar_uv(p.pos, p.nrm, 0.9 if p.tex == 'steel' else 0.5)
        out[k] = p
    top, side = concrete_pad()
    for q in (top, side):
        q.tint = np.array([1.0, 1.0, 1.0])
        out[q.name] = q
    if ao:
        ground = box_part('g', (-6, -0.3, -6), (6, 0.0, 6), None, faces='Y')
        occ = list(out.values()) + [ground]
        occ_pos = np.concatenate([q.pos for q in occ]); occ_tri = []
        off = 0
        for q in occ:
            occ_tri.append(q.tri + off); off += len(q.pos)
        occ_tri = np.concatenate(occ_tri)
        for k, p in out.items():
            a = bake_ao(p.pos, p.nrm, occ_pos, occ_tri, dirs=48, res=320, upper=True)
            p.col = np.clip(0.25 + 0.75 * a, 0, 1)[:, None] * np.ones((1, 3))
    for p in out.values():
        p.col = p.col * p.tint[None, :]
    return out


def build_tomahawk(ao=True):
    parts = tomahawk()
    if ao:
        occ = list(parts.values())
        occ_pos = np.concatenate([q.pos for q in occ]); occ_tri = []
        off = 0
        for q in occ:
            occ_tri.append(q.tri + off); off += len(q.pos)
        occ_tri = np.concatenate(occ_tri)
        for p in parts.values():
            a = bake_ao(p.pos, p.nrm, occ_pos, occ_tri, dirs=56, res=384)
            p.col = np.clip(0.35 + 0.65 * a, 0, 1)[:, None] * np.ones((1, 3))
    for p in parts.values():
        p.tex = 'tomahawk'
    return parts


def build_designator():
    d, size = designator()
    d.tex = 'designator'
    return d, size
