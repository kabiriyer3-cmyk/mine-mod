#!/usr/bin/env python3
"""Standard-library static checks. Not a Minecraft runtime test."""
from pathlib import Path
import json,struct,math,re
ROOT=Path(__file__).resolve().parents[1];R=ROOT/'src/main/resources';A=R/'assets/topol'
def require(c,m):
    if not c:raise AssertionError(m)
def tmsh(p):
    data=p.read_bytes();offset=0
    def read(fmt):
        nonlocal offset
        fmt='>'+fmt;n=struct.calcsize(fmt)
        require(offset+n<=len(data),'Truncated mesh')
        value=struct.unpack_from(fmt,data,offset);offset+=n;return value
    def text():
        nonlocal offset
        n=read('H')[0];s=data[offset:offset+n].decode('utf-8');offset+=n;return s
    require(read('ii')==(0x544d5348,1),'Mesh format')
    parts=read('i')[0];require(0<parts<32,'Parts');names=[];quads=0;bounds=[]
    for i in range(parts):
        names.append(text());read('fff');read('?');n=read('i')[0];require(0<n<50000,'Vertices')
        pos=read('f'*(n*3));normal=read('f'*(n*3));uv=read('f'*(n*2))
        require(all(math.isfinite(x) for x in (*pos,*normal,*uv)),'Nonfinite mesh')
        require(all(0<=x<=1 for x in uv),'UV')
        bounds.extend(pos[1::3]);offset+=n
        q=read('i')[0];require(0<q<25000,'Quads')
        index=read('i'*(q*4));require(all(0<=x<n for x in index),'Mesh indices');quads+=q
    for i in range(read('i')[0]):text();read('fff')
    require(offset==len(data),'Trailing mesh bytes')
    require(max(bounds)<4.1,'Unexpected large legacy model')
    print(f'MESH {p.name}: {parts} parts, {quads} quads, height {max(bounds)-min(bounds):.2f}')
    return names
def main():
    paths=list(R.rglob('*.json'))
    for p in paths:json.loads(p.read_text(encoding='utf-8'))
    en=json.loads((A/'lang/en_us.json').read_text());ru=json.loads((A/'lang/ru_ru.json').read_text())
    require(en.keys()==ru.keys(),'Language keys')
    for lang in [en,ru]:
        require(all('ядерн' not in str(v).lower() for v in lang.values()),'Legacy localization')
    for p in (A/'models/tmsh').glob('*.tmsh'):tmsh(p)
    sounds=json.loads((A/'sounds.json').read_text())
    for name,event in sounds.items():
        require(event['subtitle'] in en,'Subtitle')
        data=(A/'sounds'/f'{name}.ogg').read_bytes();i=data.find(b'\x01vorbis')
        require(data.startswith(b'OggS') and i>=0,'Audio format')
        require(data[i+11]==1 and struct.unpack_from('<I',data,i+12)[0]==44100,'Mono 44.1 kHz')
    for name in ['launcher','missile']:
        for size in [2048,1024,512]:
            b=(A/f'textures/entity/{name}_{size}.png').read_bytes()
            require(struct.unpack_from('>II',b,16)==(size,size),'Texture LOD')
    for name in ['smoke','fire']:
        b=(A/f'textures/fx/{name}.png').read_bytes();require(struct.unpack_from('>II',b,16)==(2048,2048),'FX resolution')
    source='\n'.join(p.read_text() for p in (ROOT/'src/main/java').rglob('*.java'))
    for bad in ['createExplosion(','spawnParticles(','ParticleTypes.','SoundEvents.','Blocks.FIRE','FlightPath','STAGE_1_END','TRINITITE']:
        require(bad not in source,'Unwanted old implementation: '+bad)
    for item in ['launcher','missile','designator','scorched_earth']:
        require((A/f'models/item/{item}.json').is_file(),'Item model')
    mod=json.loads((R/'fabric.mod.json').read_text())
    for entries in mod['entrypoints'].values():
        for name in entries:require((ROOT/'src/main/java'/Path(name.replace('.','/')+'.java')).is_file(),'Entry point')
    print(f'PASS: {len(paths)} JSON files, {len(en)} matching language keys, {len(sounds)} original mono sounds')
    print('Static validation only; real Minecraft/Fabric build and in-game tests still required.')
if __name__=='__main__':main()
