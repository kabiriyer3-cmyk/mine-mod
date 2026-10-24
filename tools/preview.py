#!/usr/bin/env python3
"""CPU textured mesh previews for visual QA. Not screenshots from Minecraft."""
import sys,math,copy
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools/assets'))
import models,mesh

def tel(deployed=False):
    parts,_=models.tel(0)
    parts=copy.deepcopy(parts)
    angle=math.pi/2 if deployed else 0
    g=models.G
    transform=mesh.translate(0,g['HINGE_Y'],g['HINGE_Z'])@mesh.rot_x(-angle)@mesh.translate(0,-g['HINGE_Y'],-g['HINGE_Z'])
    for p in parts:
        if p.name in ('erector','cap'):p.transform(transform)
        if p.name.startswith('jack') and deployed:p.transform(mesh.translate(0,-.47,0))
    a=np.array(g['RAM_A']);b=np.array(g['RAM_B'])
    moved=(transform@np.array([*b,1]))[:3]
    parts=[p for p in parts if p.name not in ('ram_tube','ram_rod')]
    for sign in [-1,1]:
        start=a.copy();end=moved.copy();start[0]*=sign;end[0]*=sign
        delta=end-start;length=np.linalg.norm(delta);axis=delta/length
        outer=mesh.Part('hydraulic')
        mesh.cylinder(outer,start,start+axis*min(length,3),.18,16,models.L['metal'])
        off=max(0,length-5.7)
        mesh.cylinder(outer,start+axis*off,start+axis*min(length,off+2.9),.145,16,models.L['metal'])
        off=max(0,length-2.7)
        mesh.cylinder(outer,start+axis*off,end,.105,16,models.L['chrome'])
        parts.append(outer)
    return parts

def render(parts,atlas,size=(1100,780),az=42,el=19):
    W,H=size
    az,el=map(math.radians,[az,el])
    right=np.array([math.cos(az),0,-math.sin(az)])
    up=np.array([-math.sin(az)*math.sin(el),math.cos(el),-math.cos(az)*math.sin(el)])
    eye=np.cross(right,up)
    allpos=np.concatenate([np.array(p.pos) for p in parts])
    projections=np.stack([allpos@right,-allpos@up],1)
    lo,hi=projections.min(0),projections.max(0)
    scale=min((W-70)/(hi[0]-lo[0]),(H-70)/(hi[1]-lo[1]))
    offset=(np.array([W,H])-(hi-lo)*scale)/2-lo*scale
    yy=np.linspace(0,1,H)[:,None,None]
    bg=(np.array([25,32,28])[None,None,:]*(1-yy)+np.array([57,65,52])[None,None,:]*yy)
    out=np.broadcast_to(bg,(H,W,3)).copy().astype(float)
    depth=np.full((H,W),-1e12)
    atlas=np.asarray(atlas.convert('RGBA'))
    light=np.array([-.35,.85,.4]);light/=np.linalg.norm(light)
    for part in parts:
        pos=np.array(part.pos)
        proj=np.stack([pos@right,-pos@up],1)*scale+offset
        zs=pos@eye
        normals=np.array(part.nrm)
        uv=np.array(part.uv)
        shades=np.array(part.col)*(.44+.56*np.maximum(0,normals@light))
        if part.emissive: shades=np.ones_like(shades)
        for q in part.tris:
            triangles=[(q[0],q[1],q[2])]
            if q[2]!=q[3]: triangles.append((q[0],q[2],q[3]))
            for tri in triangles:
                ids=np.array(tri)
                points=proj[ids]
                x0,y0=np.maximum(0,np.floor(points.min(0)).astype(int))
                x1,y1=np.minimum([W-1,H-1],np.ceil(points.max(0)).astype(int))
                if x1<x0 or y1<y0:continue
                a,b,c=points
                den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
                if abs(den)<1e-8:continue
                y,x=np.mgrid[y0:y1+1,x0:x1+1].astype(float)
                x+=.5;y+=.5
                w0=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/den
                w1=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/den
                w2=1-w0-w1
                w=np.stack([w0,w1,w2],-1)
                z=(w*zs[ids]).sum(-1)
                sub=depth[y0:y1+1,x0:x1+1]
                mask=(w.min(-1)>=-1e-5)&(z>sub)
                if not mask.any():continue
                sampleuv=w@uv[ids]
                u=np.clip((sampleuv[...,0]*atlas.shape[1]).astype(int),0,atlas.shape[1]-1)
                v=np.clip((sampleuv[...,1]*atlas.shape[0]).astype(int),0,atlas.shape[0]-1)
                color=atlas[v,u]
                mask&=color[...,3]>127
                sh=(w*shades[ids]).sum(-1)
                out[y0:y1+1,x0:x1+1][mask]=(color[...,:3]*sh[...,None])[mask]
                sub[mask]=z[mask]
    return Image.fromarray(out.clip(0,255).astype('uint8'))

if __name__=='__main__':
    atlas=Image.open(ROOT/'src/main/resources/assets/topol/textures/entity/launcher_2048.png')
    stowed=render(tel(False),atlas,az=45,el=23)
    raised=render(tel(True),atlas,az=135,el=13)
    out=Image.new('RGB',(2200,840),'#151e18')
    out.paste(stowed,(0,60));out.paste(raised,(1100,60))
    d=ImageDraw.Draw(out)
    f=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',23)
    d.text((35,18),'STOWED / SOFTWARE ASSET PREVIEW',fill='#c9d7b6',font=f)
    d.text((1135,18),'DEPLOYED / NOT AN IN-GAME SCREENSHOT',fill='#c9d7b6',font=f)
    p=ROOT/'docs/model-preview.png';p.parent.mkdir(exist_ok=True);out.save(p)
    print(p)
