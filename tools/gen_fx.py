#!/usr/bin/env python3
"""Original FX sprite sheets (no vanilla textures). smoke.png: 8x8 cauliflower puffs, RGB = top-lit luminance,
A = density. fire/glow/plume/spark/scorch/debris come from tools/assets/fxtex.py."""
from pathlib import Path
import sys
import numpy as np
from PIL import Image
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools/models')); sys.path.insert(0, str(ROOT / 'tools/assets'))
import smoke2
A = ROOT / 'src/main/resources/assets/topol/textures/fx'


def smoke_atlas(cells=8, n=256):
    out = np.zeros((cells * n, cells * n, 4), np.float32)
    for i in range(cells * cells):
        lum, al = smoke2.puff(n, 100 + i * 7, blobs=7 + i % 5)
        y, x = divmod(i, cells)
        out[y * n:(y + 1) * n, x * n:(x + 1) * n] = np.dstack([lum, lum, lum * 0.985, al])
    return Image.fromarray((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA')


if __name__ == '__main__':
    A.mkdir(parents=True, exist_ok=True)
    smoke_atlas().save(A / 'smoke.png', optimize=True)
    if '--all' in sys.argv:
        import fxtex
        for name, fn in [('fire', fxtex.fire_atlas), ('glow', fxtex.glow), ('plume', fxtex.plume), ('spark', fxtex.spark),
                         ('scorch', fxtex.scorch), ('debris', fxtex.debris_tex)]:
            fn().save(A / f'{name}.png', optimize=True)
    print('FX sprites written')
