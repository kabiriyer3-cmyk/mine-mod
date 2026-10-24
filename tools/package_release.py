#!/usr/bin/env python3
"""Source-only packager. Checks contents, excludes inherited unused source/FX/audio, hashes every file."""
from pathlib import Path
import json,hashlib,zipfile,sys,re
import validate_assets
ROOT=Path(__file__).resolve().parents[1]
VERSION='0.3.0-silo-rc1'
EXCLUDED={'.git','.gradle','build','run','out','dist','__pycache__','.idea','.vscode'}
def collect():
    sounds=set(json.loads((ROOT/'src/main/resources/assets/topol/sounds.json').read_text()))
    paths=[]
    for p in ROOT.rglob('*'):
        if not p.is_file():continue
        rel=p.relative_to(ROOT)
        if any(part in EXCLUDED for part in rel.parts):continue
        if p.suffix in {'.pyc','.class','.jar','.zip','.log'} or p.name=='RELEASE_MANIFEST.json':continue
        if rel.as_posix()=='tools/assets/models.py':continue
        if p.suffix=='.ogg' and p.stem not in sounds:continue
        if rel.as_posix() in {'src/main/resources/assets/topol/textures/fx/ring.png','src/main/resources/assets/topol/textures/fx/flare.png'}:continue
        if p.name.startswith('.env') or p.suffix in {'.pem','.key'}:raise RuntimeError('Unexpected sensitive file')
        if p.suffix in {'.java','.json','.py','.md','.yml','.gradle','.properties'}:
            text=p.read_text(encoding='utf-8')
            if re.search(r'ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|BEGIN (?:RSA |OPENSSH )?PRIVATE KEY',text):
                raise RuntimeError('Unexpected secret-like content')
        paths.append(p)
    return sorted(paths)
def main():
    validate_assets.main()
    paths=collect()
    for n in ['README.md','VALIDATION.md','TEST_CHECKLIST.md','.github/workflows/build.yml','docs/DEJAVU_LICENSE.txt']:
        assert (ROOT/n) in paths,n
    manifest={'name':'Solo Silo: Conventional Rockets','version':VERSION,'minecraft':'1.21.1','type':'source-only',
       'verified':['8 standalone game-math tests','static resources','Java syntax with private API doubles','software geometry preview'],
       'not_verified':['real Minecraft/Fabric Gradle build','client/server runtime','shader compatibility'],
       'files':{p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}}
    target=Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'dist'/f'solo-silo-fabric-1.21.1-{VERSION}-source.zip'
    target.parent.mkdir(parents=True,exist_ok=True);prefix=f'solo-silo-fabric-1.21.1-{VERSION}'
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for p in paths:
            info=zipfile.ZipInfo(prefix+'/'+p.relative_to(ROOT).as_posix(),(2026,10,24,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            z.writestr(info,p.read_bytes())
        z.writestr(prefix+'/RELEASE_MANIFEST.json',json.dumps(manifest,indent=2)+'\n')
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None
        m=json.loads(z.read(prefix+'/RELEASE_MANIFEST.json'))
        for name,expected in m['files'].items():assert hashlib.sha256(z.read(prefix+'/'+name)).hexdigest()==expected,name
        assert not any('/api-stubs/' in n or '/__pycache__/' in n or n.endswith(('.pyc','.class','.jar')) for n in z.namelist())
    print('ZIP verified:',target.name)
    print('Files:',len(paths)+1,'bytes:',target.stat().st_size)
    print('SHA256:',hashlib.sha256(target.read_bytes()).hexdigest())
if __name__=='__main__':main()
