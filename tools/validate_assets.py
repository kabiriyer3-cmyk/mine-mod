#!/usr/bin/env python3
"""Standard-library static checks run by CI before Gradle. Not a Minecraft runtime test."""
from pathlib import Path
import json, struct, math, re
ROOT = Path(__file__).resolve().parents[1]; R = ROOT / 'src/main/resources'; A = R / 'assets/topol'
TEXTURES = {'tomahawk': 2048, 'designator': 1024, 'steel': 1024, 'grip': 512, 'concrete': 1024, 'pad': 1024}
MESHES = {'silo': {'pad_top', 'pad_side', 'hatch_4', 'hatch_5'},
          'missile': {'body', 'intake', 'booster', 'fin0', 'fin1', 'fin2', 'fin3', 'wing_l', 'wing_r'},
          'designator': {'body'}}


def require(c, m):
    if not c: raise AssertionError(m)


def png_size(p):
    b = p.read_bytes(); require(b[:8] == b'\x89PNG\r\n\x1a\n', f'PNG {p.name}')
    return struct.unpack_from('>II', b, 16)


def tmsh(p):
    data = p.read_bytes(); off = 0
    def read(fmt):
        nonlocal off
        fmt = '>' + fmt; n = struct.calcsize(fmt)
        require(off + n <= len(data), 'Truncated mesh ' + p.name)
        v = struct.unpack_from(fmt, data, off); off += n; return v
    def text():
        nonlocal off
        n = read('H')[0]; s = data[off:off + n].decode('utf-8'); off += n; return s
    require(data[:4] == b'TMSH', 'Mesh magic'); off = 4
    require(read('i')[0] == 2, 'TMSH v2')
    parts = read('i')[0]; require(0 < parts <= 64, 'Parts')
    names, tris, lo, hi = {}, 0, [1e9] * 3, [-1e9] * 3
    for _ in range(parts):
        name, tex = text(), text()
        require(re.fullmatch(r'[a-z0-9_]{1,32}', tex) is not None, 'Texture key')
        require(tex in TEXTURES, 'Unknown texture ' + tex)
        read('b'); piv = read('6f'); require(all(math.isfinite(x) for x in piv), 'Pivot')
        n = read('i')[0]; require(0 < n <= 200000, 'Vertices')
        stride = 12 + 3 + 8 + 3
        for v in range(n):
            x, y, z = struct.unpack_from('>3f', data, off + v * stride)
            u, w = struct.unpack_from('>2f', data, off + v * stride + 15)
            require(all(math.isfinite(k) for k in (x, y, z, u, w)), 'Non-finite vertex')
            lo = [min(a, b) for a, b in zip(lo, (x, y, z))]; hi = [max(a, b) for a, b in zip(hi, (x, y, z))]
        off += n * stride
        t = read('i')[0]; require(0 < t <= 200000, 'Triangles')
        idx = struct.unpack_from(f'>{t * 3}i', data, off); off += t * 12
        require(min(idx) >= 0 and max(idx) < n, 'Mesh indices')
        names[name] = tex; tris += t
    require(off == len(data), 'Trailing mesh bytes')
    print(f'MESH {p.name}: {parts} parts, {tris} tris, bounds {[round(a, 2) for a in lo]} .. {[round(b, 2) for b in hi]}')
    return names, lo, hi


def main():
    paths = list(R.rglob('*.json')) + list(R.rglob('*.mcmeta'))
    for p in paths: json.loads(p.read_text(encoding='utf-8'))
    en = json.loads((A / 'lang/en_us.json').read_text()); ru = json.loads((A / 'lang/ru_ru.json').read_text())
    require(en.keys() == ru.keys(), 'Language keys')
    for lang in [en, ru]:
        require(all('ядерн' not in str(v).lower() for v in lang.values()), 'Legacy localization')
    for name, needed in MESHES.items():
        names, lo, hi = tmsh(A / f'models/tmsh/{name}.tmsh')
        require(needed <= names.keys(), f'{name} parts {needed - names.keys()}')
        if name == 'missile': require(abs(hi[1] - 6.25) < .01 and abs(lo[1]) < .01, 'Missile is 6.25 blocks, tail at 0')
        if name == 'silo': require(lo[1] < -6.5 and hi[1] < .6, 'Silo buried 7 blocks, deck near ground')
        if name == 'designator': require(max(h - l for h, l in zip(hi, lo)) < .2, 'Designator in metres')
    for key, full in TEXTURES.items():
        for lod in range(3):
            p = A / f'textures/entity/{key}_{lod}.png'
            require(png_size(p) == (full >> lod, full >> lod), 'Texture LOD ' + p.name)
            meta = json.loads((p.parent / (p.name + '.mcmeta')).read_text())
            require(meta['texture']['blur'] is True, 'Linear filtering ' + p.name)
    for name in ['smoke', 'fire']:
        require(png_size(A / f'textures/fx/{name}.png') == (2048, 2048), 'FX resolution')
    for name in ['glow', 'plume', 'spark', 'debris', 'scorch', 'laser']:
        png_size(A / f'textures/fx/{name}.png')
    for item in ['launcher', 'missile']:
        require(png_size(A / f'textures/item/{item}.png') == (128, 128), 'Item icon')
    sounds = json.loads((A / 'sounds.json').read_text())
    for name, event in sounds.items():
        require(event['subtitle'] in en, 'Subtitle')
        data = (A / 'sounds' / f'{name}.ogg').read_bytes(); i = data.find(b'\x01vorbis')
        require(data.startswith(b'OggS') and i >= 0, 'Audio format')
        require(data[i + 11] == 1 and struct.unpack_from('<I', data, i + 12)[0] == 44100, 'Mono 44.1 kHz')
    source = '\n'.join(p.read_text() for p in (ROOT / 'src/main/java').rglob('*.java'))
    for bad in ['createExplosion(', 'spawnParticles(', 'addParticle(', 'ParticleTypes.', 'SoundEvents.', 'Blocks.FIRE', 'FlightPath', 'TRINITITE']:
        require(bad not in source, 'Vanilla effect / old implementation: ' + bad)
    for item in ['launcher', 'missile', 'designator', 'scorched_earth']:
        require((A / f'models/item/{item}.json').is_file(), 'Item model')
    des = json.loads((A / 'models/item/designator.json').read_text())
    require(des['parent'] == 'minecraft:builtin/entity', 'Designator is a 3D builtin item')
    mod = json.loads((R / 'fabric.mod.json').read_text())
    for entries in mod['entrypoints'].values():
        for name in entries: require((ROOT / 'src/main/java' / Path(name.replace('.', '/') + '.java')).is_file(), 'Entry point')
    notices = (ROOT / 'THIRD_PARTY_NOTICES.md').read_text()
    for credit in ['RickSlash', 'DenisGLabrecque', 'ikoval31', 'CC-BY-4.0']:
        require(credit in notices, 'CC BY credit missing: ' + credit)
    print(f'PASS: {len(paths)} JSON files, {len(en)} matching language keys, {len(sounds)} original mono sounds')
    print('Static validation only; real Minecraft/Fabric build and in-game tests still required.')


if __name__ == '__main__': main()
