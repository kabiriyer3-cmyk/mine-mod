#!/usr/bin/env python3
"""Converts the three CC BY 4.0 Sketchfab models into the mod's runtime assets.

Inputs (not shipped, download them yourself, see THIRD_PARTY_NOTICES.md):
  tools/models/src/tomahawk/scene.gltf   BGM-109 Tomahawk missile, RickSlash
  tools/models/src/vls/scene.gltf        Vertical Launch System missile canisters, DenisGLabrecque
  tools/models/src/perst4/scene.gltf     Zenitco Perst-4 laser pointer, ikoval31
  (or set SILO_MODEL_SRC to a folder with those three sub-folders)

Outputs (shipped, the Gradle build never needs Python):
  assets/topol/models/tmsh/{silo,missile,designator}.tmsh   TMSH v2 meshes
  assets/topol/textures/entity/<name>_{0,1,2}.png (+ .mcmeta)  texture LOD chain
  assets/topol/textures/item/{launcher,missile}.png, icon.png  pre-rendered icons

python3 tools/gen_models.py [--cache assembled.pkl --tex-cache dir]   (full AO bake takes ~4 min)
"""
from pathlib import Path
import sys, json, math, struct, pickle, argparse
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools/models'))
A = ROOT / 'src/main/resources/assets/topol'

# texture key -> full size; LOD i is size >> i
TEXTURES = {'tomahawk': 2048, 'designator': 1024, 'steel': 1024, 'grip': 512, 'concrete': 1024, 'pad': 1024}
WING_PIVOT = (0.0, 3.242, 0.0)


def build_parts(cache):
    if cache:
        sys.modules.setdefault('parts', __import__('parts'))
        return pickle.load(open(cache, 'rb'))
    import assemble
    return {'silo': assemble.build_silo(), 'toma': assemble.build_tomahawk(), 'des': assemble.build_designator()[0]}


def build_textures(tex_cache):
    if tex_cache:
        return {k: Image.open(Path(tex_cache) / f'{k}.png').convert('RGB') for k in TEXTURES}
    import textures as T
    return {'tomahawk': T.tomahawk_albedo(2048), 'designator': T.perst_albedo(1024), 'steel': T.painted_steel(1024),
            'grip': T.grip_pad(512), 'concrete': T.concrete(1024), 'pad': T.pad_decal(1024)}


def write_tmsh(path, parts):
    """TMSH v2, big-endian (Java DataInputStream):
    'TMSH' int 2, int parts; per part: UTF name, UTF texture, byte flags, float3 pivot, float3 axis,
    int n, n*(float3 pos, byte3 normal, float2 uv, ubyte3 colour), int t, t*3 int indices"""
    out = bytearray(b'TMSH') + struct.pack('>ii', 2, len(parts))
    def utf(s):
        b = s.encode('utf-8'); return struct.pack('>H', len(b)) + b
    for name, tex, p, pivot, axis in parts:
        out += utf(name) + utf(tex) + struct.pack('>b', 0) + struct.pack('>6f', *pivot, *axis)
        n = len(p.pos)
        nrm = p.nrm / np.maximum(np.linalg.norm(p.nrm, axis=1, keepdims=True), 1e-9)
        col = np.clip(np.round(p.col * 255), 0, 255).astype(np.uint8)
        rec = np.zeros(n, dtype=[('p', '>f4', 3), ('n', 'i1', 3), ('uv', '>f4', 2), ('c', 'u1', 3)])
        rec['p'] = p.pos; rec['n'] = np.clip(np.round(nrm * 127), -127, 127); rec['uv'] = p.uv; rec['c'] = col
        out += struct.pack('>i', n) + rec.tobytes()
        tri = np.asarray(p.tri, '>i4')
        assert tri.min() >= 0 and tri.max() < n
        out += struct.pack('>i', len(tri)) + tri.tobytes()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(bytes(out))
    print(f'{path.name}: {len(parts)} parts, {sum(len(p[2].tri) for p in parts)} tris, {len(out) // 1024} KiB')


def missile_parts(toma):
    parts = []
    for k in ['body', 'intake', 'booster', 'fin0', 'fin1', 'fin2', 'fin3', 'wing_l', 'wing_r']:
        p = toma[k]
        if k.startswith('fin'):
            c = p.pos.mean(0); d = np.array([c[0] - p.pivot[0], 0, c[2] - p.pivot[2]]); d /= np.linalg.norm(d)
            parts.append((k, 'tomahawk', p, tuple(p.pivot), tuple(d)))
        elif k.startswith('wing'):
            parts.append((k, 'tomahawk', p, WING_PIVOT, (0, 0, 1)))
        else:
            parts.append((k, 'tomahawk', p, (0, 0, 0), (0, 1, 0)))
    return parts


def designator_part(d):
    """lens (+X in the source) -> -Z: forward for first-person rendering; top stays +Y"""
    class Q: pass
    q = Q()
    rot = lambda v: np.stack([v[:, 2], v[:, 1], -v[:, 0]], 1)
    q.pos, q.nrm, q.uv, q.tri, q.col = rot(d.pos), rot(d.nrm), d.uv, d.tri, d.col
    return [('body', 'designator', q, (0, 0, 0), (0, 0, -1))]


def save_textures(tex):
    for k, full in TEXTURES.items():
        im = tex[k].convert('RGB')
        for lod in range(3):
            s = full >> lod
            p = A / f'textures/entity/{k}_{lod}.png'
            p.parent.mkdir(parents=True, exist_ok=True)
            im.resize((s, s), Image.LANCZOS).save(p, optimize=True)
            (p.parent / (p.name + '.mcmeta')).write_text(json.dumps({'texture': {'blur': True, 'clamp': False}}) + '\n')


def icon(meshes, eye, target, size, fov, up=(0, 1, 0)):
    from raster import render
    img, zb, cam = render(meshes, eye=eye, target=target, size=(size * 2, size * 2), fov=fov, ss=2, raw=True, up=up)
    H, W = zb.shape[:2]
    mask = np.isfinite(zb) & (zb < 1e8)
    rgb = np.clip(img[:H, :W], 0, 1)
    a = mask.astype(np.float32)
    im = Image.fromarray(np.dstack([(rgb * 255 + .5).astype(np.uint8), (a * 255).astype(np.uint8)]), 'RGBA')
    bb = im.getbbox()
    if bb:
        im = im.crop(bb)
    w, h = im.size; s = max(w, h)
    canvas = Image.new('RGBA', (s, s)); canvas.paste(im, ((s - w) // 2, (s - h) // 2))
    return canvas.resize((size, size), Image.LANCZOS)


def icons(parts, tex):
    from raster import Mesh
    T = {k: np.asarray(v.convert('RGB').resize((1024, 1024) if k != 'grip' else (512, 512))) for k, v in tex.items()}
    mk = lambda p, R=np.eye(3), t=np.zeros(3): Mesh(p.pos @ R.T + t, p.nrm @ R.T, p.uv, p.col, p.tri, T.get(p.tex))
    # missile: wings deployed, lying diagonally across the icon
    a = math.radians(-45)
    R = np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])
    ms = [mk(p, R) for k, p in parts['toma'].items()]
    missile = icon(ms, eye=(0.0, 3.1, 9.5), target=(2.2, 2.2, 0), size=128, fov=38)
    silo = [mk(p) for k, p in parts['silo'].items() if not k.startswith(('silos', 'trusses', 'pipes'))]
    launcher = icon(silo, eye=(2.6, 2.4, 3.2), target=(0, 0.05, 0.12), size=128, fov=38)
    for name, im in (('launcher', launcher), ('missile', missile)):
        p = A / f'textures/item/{name}.png'; p.parent.mkdir(parents=True, exist_ok=True); im.save(p, optimize=True)
    launcher.save(A / 'icon.png', optimize=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--cache'); ap.add_argument('--tex-cache')
    args = ap.parse_args()
    parts = build_parts(args.cache)
    tex = build_textures(args.tex_cache)
    silo = [(k, p.tex, p, tuple(p.pivot), (1, 0, 0)) for k, p in parts['silo'].items()]
    write_tmsh(A / 'models/tmsh/silo.tmsh', silo)
    write_tmsh(A / 'models/tmsh/missile.tmsh', missile_parts(parts['toma']))
    write_tmsh(A / 'models/tmsh/designator.tmsh', designator_part(parts['des']))
    save_textures(tex)
    icons(parts, tex)


if __name__ == '__main__':
    main()
