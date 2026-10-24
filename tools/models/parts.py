"""Turns the three CC-BY glTF models into named, animatable parts in mod space (metres, +Y up)."""
import math
import numpy as np
from gltf import Gltf

import os
SRC = os.environ.get('SILO_MODEL_SRC', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'src'))


class P:
    """one part: triangle soup in mod space"""
    def __init__(self, name, pos, nrm, uv, tri, mat, pivot=(0, 0, 0)):
        self.name, self.pos, self.nrm, self.uv, self.tri, self.mat = name, pos, nrm, uv, tri, mat
        self.pivot = np.asarray(pivot, float)
        self.col = np.ones((len(pos), 3))

    @staticmethod
    def merge(name, prims, mat=None, pivot=(0, 0, 0)):
        pos, nrm, uv, tri, off = [], [], [], [], 0
        for p in prims:
            pos.append(p['pos']); nrm.append(p['nrm']); uv.append(p['uv']); tri.append(p['tri'] + off); off += len(p['pos'])
        return P(name, np.concatenate(pos), np.concatenate(nrm), np.concatenate(uv), np.concatenate(tri), mat if mat is not None else prims[0]['material'], pivot)


def xform(prims, m3, t, s):
    """apply mod-space mapping x' = s * (m3 @ x) + t to positions, rotate normals"""
    out = []
    for p in prims:
        q = dict(p)
        q['pos'] = (p['pos'] @ m3.T) * s + t
        q['nrm'] = p['nrm'] @ m3.T
        q['tri'] = p['tri'] if np.linalg.det(m3) > 0 else p['tri'][:, ::-1]
        out.append(q)
    return out


# ------------------------------------------------------------------ Tomahawk
# model: nose +Z, top +Y, length 23.49 units (real ~6.25 m with booster). Mod: nose +Y, top -Z, tail at y = 0.
TOMA_S = 6.25 / 23.485
ROT_TOMA = np.array([[1, 0, 0], [0, 0, 1], [0, -1, 0.0]])   # (x, y, z) -> (x, z, -y): rotation -90 deg about X


def tomahawk():
    g = Gltf(f'{SRC}/tomahawk/scene.gltf')
    prims = g.primitives()
    tail = min(p['pos'][:, 2].min() for p in prims)
    prims = xform(prims, ROT_TOMA, np.array([0, -tail * TOMA_S, 0]), TOMA_S)
    by = {}
    for p in prims:
        by.setdefault(p['names'][-2], []).append(p)
    parts = {}
    parts['body'] = P.merge('body', by['tomahawk_body'] + by['tomahawk_connect'])
    parts['intake'] = P.merge('intake', by['tomahawk_aigrab'])
    parts['booster'] = P.merge('booster', by['start_engine'])
    for k, clock in enumerate((3, 6, 9, 12)):
        fin = by[f'wing_back_{clock}'] + by[f'wing_connect_{clock}'] + by[f'wing_core_{clock}']
        pf = P.merge(f'fin{k}', fin)
        # hinge at the root of the fin, on the body surface, fins fold flat along the body axis
        root = pf.pos[np.argmin(np.linalg.norm(pf.pos[:, [0, 2]], axis=1))]
        pf.pivot = np.array([root[0], pf.pos[:, 1].max(), root[2]])
        parts[pf.name] = pf
    for side, nm in ((1, 'plane_left'), (-1, 'plane_right')):
        pw = P.merge('wing_l' if side > 0 else 'wing_r', by[nm])
        # scissor wings pivot near the inboard leading edge
        inb = pw.pos[np.abs(pw.pos[:, 0]) < np.abs(pw.pos[:, 0]).min() + 0.02]
        pw.pivot = np.array([inb[:, 0].mean(), inb[:, 1].max(), inb[:, 2].mean()])
        parts[pw.name] = pw
    return parts


# ------------------------------------------------------------------ VLS cell -> solo silo
VLS_S = 7.0 / 15.0      # canister height 15 units -> 7 m (Mk 41-like tactical length)
DECK_Y = 15.11           # top of the deck plates
PROTRUDE = 0.25          # how much of the canister head stays above the ground (m)


def silo():
    g = Gltf(f'{SRC}/vls/scene.gltf')
    prims = g.primitives()
    sel = []
    hatch = []
    for p in prims:
        nm = p['names'][-2]
        if nm.startswith(('Fins', 'Missile', 'Trapdoor', 'SmokeVent')) or '.001' in nm:
            if nm.startswith('TrapdoorD'):
                q = dict(p); q['pos'] = p['pos'] + np.array([-9.4655, 0, 0]); hatch.append(q)
            continue
        c = p['pos'][p['tri']].mean(1)
        keep = (c[:, 0] > -1.75) & (c[:, 0] < 1.62) & (c[:, 2] > -2.1) & (c[:, 2] < 2.35)
        if keep.any():
            q = dict(p)
            used = p['tri'][keep]
            uniq, inv = np.unique(used, return_inverse=True)
            q['pos'], q['nrm'], q['uv'] = p['pos'][uniq], p['nrm'][uniq], p['uv'][uniq]
            q['tri'] = inv.reshape(-1, 3)
            sel.append(q)
    t = np.array([0, -DECK_Y * VLS_S + PROTRUDE, 0])
    sel = xform(sel, np.eye(3), t, VLS_S)
    hatch = xform(hatch, np.eye(3), t, VLS_S)
    parts = {}
    groups = {}
    for p in sel:
        nm = p['names'][-2].split('_')[0]
        groups.setdefault((nm, p['material']), []).append(p)
    for (nm, mat), ps in groups.items():
        parts[f'{nm.lower()}_{mat}'] = P.merge(f'{nm.lower()}_{mat}', ps, mat)
    for mat in sorted({p['material'] for p in hatch}):
        ph = P.merge(f'hatch_{mat}', [p for p in hatch if p['material'] == mat], mat)
        parts[ph.name] = ph
    hp = np.concatenate([p['pos'] for p in hatch])
    hinge = np.array([0, (15.235 * VLS_S) + t[1], 1.7309 * VLS_S])
    for k in parts:
        if k.startswith('hatch'):
            parts[k].pivot = hinge
    return parts, g.j['materials']


# ------------------------------------------------------------------ Perst-4 designator
def designator():
    g = Gltf(f'{SRC}/perst4/scene.gltf')
    prims = g.primitives()
    allp = np.concatenate([p['pos'] for p in prims])
    c = (allp.min(0) + allp.max(0)) / 2
    sz = allp.max(0) - allp.min(0)
    prims = xform(prims, np.eye(3), -c * 0.01, 0.01)    # centimetres -> metres, centred
    return P.merge('designator', prims), sz * 0.01
