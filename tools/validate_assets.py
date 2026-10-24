#!/usr/bin/env python3
"""Standard-library validation, run in CI before Gradle. Never downloads or edits anything."""
from pathlib import Path
import json, math, struct, sys

ROOT=Path(__file__).resolve().parents[1]
R=ROOT/'src/main/resources'
A=R/'assets/topol'
def require(ok,message):
    if not ok: raise AssertionError(message)
def tmsh(path):
    data=memoryview(path.read_bytes());off=0
    def read(fmt):
        nonlocal off
        fmt='>'+fmt
        n=struct.calcsize(fmt)
        require(off+n<=len(data),f'truncated {path.name}')
        v=struct.unpack_from(fmt,data,off);off+=n
        return v
    def name():
        nonlocal off
        n=read('H')[0];s=bytes(data[off:off+n]).decode('utf-8');off+=n;return s
    require(read('ii')==(0x544d5348,1),'TMSH header')
    parts=read('i')[0];require(0<parts<128,'part count')
    total=0
    for _ in range(parts):
        part=name();pivot=read('fff');read('?');count=read('i')[0]
        require(0<count<300000,'vertex count')
        p=read('f'*(count*3));norm=read('f'*(count*3));uv=read('f'*(count*2))
        require(all(math.isfinite(v) for v in (*p,*norm,*uv,*pivot)),f'non-finite {part}')
        require(all(-.0001<=v<=1.0001 for v in uv),f'UV outside atlas: {part}')
        off+=count
        nquad=read('i')[0];require(0<nquad<150000,'quad count')
        index=read('i'*(nquad*4));require(all(0<=v<count for v in index),f'index {part}')
        total+=nquad
    anchors=read('i')[0];require(0<=anchors<128,'anchor count')
    for _ in range(anchors): name();read('fff')
    require(off==len(data),'trailing mesh data')
    return parts,total
def main():
    files=list(R.rglob('*'));jsons=[p for p in files if p.suffix=='.json']
    for p in jsons: json.loads(p.read_text(encoding='utf-8'))
    en=json.loads((A/'lang/en_us.json').read_text());ru=json.loads((A/'lang/ru_ru.json').read_text())
    require(set(en)==set(ru),'Translation key mismatch')
    for n in range(6): require(f'state.topol.{n}' in en,'Missing state translation')
    for n in range(16): require(f'message.topol.{n}' in en,'Missing message translation')
    sounds=json.loads((A/'sounds.json').read_text())
    for sound,event in sounds.items():
        require(event['subtitle'] in en,f'Subtitle {sound}')
        for item in event['sounds']:
            f=A/'sounds'/f"{item['name'].split(':',1)[1]}.ogg"
            data=f.read_bytes()
            require(data[:4]==b'OggS',f'not Ogg {f.name}')
            i=data.find(b'\x01vorbis');require(i>=0,'not Vorbis')
            require(data[i+11]==1,f'not mono {f.name}')
            require(struct.unpack_from('<I',data,i+12)[0]==44100,f'sample rate {f.name}')
    for p in A.rglob('*.png'):
        data=p.read_bytes()
        require(data[:8]==b'\x89PNG\r\n\x1a\n',f'not PNG {p.name}')
        w,h=struct.unpack_from('>II',data,16)
        require(w>0 and h>0 and w<=4096 and h<=4096,f'PNG dimensions {p.name}')
        if p.stem.endswith('_2048'): require((w,h)==(2048,2048),'HD atlas size')
        if p.stem in ('smoke','fire'): require((w,h)==(2048,2048),'FX atlas size')
    total=0
    for p in sorted((A/'models/tmsh').glob('*.tmsh')):
        parts,quads=tmsh(p);total+=quads
        print(f'MESH OK {p.name}: {parts} parts, {quads} quads')
    mod=json.loads((R/'fabric.mod.json').read_text())
    for entries in mod['entrypoints'].values():
        for e in entries:require((ROOT/'src/main/java'/Path(e.replace('.','/')+'.java')).is_file(),f'entry point {e}')
    for mixin in mod['mixins']:
        conf=json.loads((R/mixin['config']).read_text())
        for name in conf['client']:
            path=ROOT/'src/main/java'/Path((conf['package']+'.'+name).replace('.','/')+'.java')
            require(path.is_file(),f'mixin source {path}')
    for item in ('launcher','missile','scorched_earth','trinitite'):
        require((A/f'models/item/{item}.json').is_file(),f'item model {item}')
    for p in (ROOT/'src/main/java').rglob('*.java'):
        text=p.read_text()
        for forbidden in ['ParticleTypes.','spawnParticles(','createExplosion(','Blocks.FIRE','SoundEvents.']:
            require(forbidden not in text,f'Vanilla FX path found: {p.name}: {forbidden}')
    print(f'PASS: {len(jsons)} JSON files, {len(en)} matching language keys, {len(sounds)} original mono sounds, {total} mesh quads (both LODs).')
    print('This is static resource validation, NOT a Minecraft launch or rendering test.')
if __name__=='__main__':
    try:main()
    except Exception as e:
        print('FAIL:',e,file=sys.stderr);sys.exit(1)
