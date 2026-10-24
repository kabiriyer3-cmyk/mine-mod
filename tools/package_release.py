#!/usr/bin/env python3
"""Package a source-only release after validating assets. Stdlib only."""
from pathlib import Path
import hashlib,json,sys,zipfile
import validate_assets

ROOT=Path(__file__).resolve().parents[1]
EXCLUDED={'.git','.gradle','build','run','dist','out','__pycache__','.idea','.vscode'}
VERSION='0.2.0-rc1'
def files():
    result=[]
    for p in ROOT.rglob('*'):
        if not p.is_file():continue
        rel=p.relative_to(ROOT)
        if any(x in EXCLUDED for x in rel.parts):continue
        if p.name=='RELEASE_MANIFEST.json':continue
        if p.suffix in {'.pyc','.class','.jar','.zip','.log'}:continue
        if p.name.startswith('.env'):raise RuntimeError('Unexpected environment file in source tree')
        result.append(p)
    return sorted(result)
def main():
    validate_assets.main()
    paths=files()
    needed=['README.md','LICENSE','THIRD_PARTY_NOTICES.md','docs/VALIDATION.md','docs/TEST_CHECKLIST.md',
            'docs/DEJAVU_LICENSE.txt','.github/workflows/build.yml']
    for name in needed:
        if not (ROOT/name).is_file():raise RuntimeError('Missing release file: '+name)
    manifest={'mod':'topol','version':VERSION,'minecraft':'1.21.1','format':'source-only',
              'verified':['seven standalone game-math tests','static Java syntax with private API doubles','resource validation','software mesh preview'],
              'not_verified':['real Minecraft/Fabric Gradle build for this RC','Minecraft client or server runtime','shader compatibility'],
              'files':{str(p.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}}
    m=ROOT/'RELEASE_MANIFEST.json'
    m.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    paths.append(m)
    target=Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'dist'/f'topol-fabric-1.21.1-{VERSION}-source.zip'
    target.parent.mkdir(parents=True,exist_ok=True)
    prefix=f'topol-fabric-1.21.1-{VERSION}'
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for p in paths:
            info=zipfile.ZipInfo(prefix+'/'+p.relative_to(ROOT).as_posix(),date_time=(2026,10,24,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=(0o100644<<16)
            z.writestr(info,p.read_bytes())
    with zipfile.ZipFile(target) as z:
        if z.testzip() is not None:raise RuntimeError('ZIP integrity failed')
    print('SOURCE ZIP:',target.name)
    print('FILES:',len(paths))
    print('BYTES:',target.stat().st_size)
    print('SHA256:',hashlib.sha256(target.read_bytes()).hexdigest())
if __name__=='__main__':main()
