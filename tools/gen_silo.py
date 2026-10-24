"""Build original compact silo artwork, short conventional audio, item models and game data.
Generated assets ship in the ZIP; no Python packages are needed for the normal Gradle build.
"""
from pathlib import Path
import sys,json,math,subprocess,wave,tempfile
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from scipy.signal import butter,sosfilt
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools/assets'))
import mesh,tex,fxtex
import silo_models
A=ROOT/'src/main/resources/assets/topol'
R=ROOT/'src/main/resources'
SOUNDS=['clunk','cap','eject','ignition','roar_near','roar_far','sonic_boom','blast_near','blast_far','ui_beep','ui_confirm','ui_error','earth']
def js(path,obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def save(image,path):
    p=A/path;p.parent.mkdir(parents=True,exist_ok=True);image.save(p,optimize=True)
def atlases():
    mark=Image.new('RGBA',(496,496))
    for i,text in enumerate(['SILO / 01','CLEAR ABOVE','SINGLE ROUND','REMOTE LINK']):
        d=tex.text_decal([text],496,124,size=48);mark.alpha_composite(d,(0,124*i))
    tiles=[tex.paint(1),tex.paint(2,'#46502f'),tex.paint(3,'#29301f',wear=.5),tex.metal(4),
           tex.chrome(5),tex.tread(6),tex.sidewall(7),tex.grille(8),tex.glass(9),
           tex.paint(10,'#4c5836'),tex.rubber(11),tex.stripes(12),tex.cab_front(13),mark,tex.lights_tile(),tex.cap_tile(14)]
    m=[tex.missile_paint(21,'#a3a895'),tex.missile_paint(22,'#87927d'),tex.missile_paint(23,'#393f32'),tex.metal(24),
       tex.nozzle_inner(25),tex.nozzle_outer(26),tex.grid_metal(27),tex.insulation(28),tex.heatshield(29),tex.heatshield(30),
       mark,tex.band_tile(31),tex.missile_paint(32,'#dbd9cb'),tex.metal(33),tex.rubber(34),tex.stripes(35)]
    result={}
    for name,ts in [('launcher',tiles),('missile',m)]:
        im=Image.new('RGBA',(2048,2048))
        for i,tile in enumerate(ts):
            im.paste(tex.pad_clamp(tile),((i%4)*512,(i//4)*512))
        for size in [2048,1024,512]:save(im.resize((size,size),Image.Resampling.LANCZOS),f'textures/entity/{name}_{size}.png')
        result[name]=im
    return result
def geometry():
    out={};(A/'models/tmsh').mkdir(parents=True,exist_ok=True)
    for name,fn in [('launcher',silo_models.silo),('missile',silo_models.rocket),('remote',silo_models.remote)]:
        for lod in [0,1]:
            parts,anchors=fn(lod)
            mesh.write_tmsh(A/f'models/tmsh/{name}_{lod}.tmsh',parts,anchors)
            print(name,lod,sum(len(p.tris) for p in parts),'quads',flush=True)
            if lod==0:out[name]=parts
    return out
def icon(parts,atlas):
    im=Image.new('RGBA',(512,512));d=ImageDraw.Draw(im)
    right=np.array([.8,0,-.6]);up=np.array([-.18,.954,-.24]);eye=np.cross(right,up)
    points=np.concatenate([np.asarray(p.pos) for p in parts])
    projected=np.stack([points@right,-points@up],1);lo=projected.min(0);hi=projected.max(0)
    scale=470/max(hi-lo);offset=(512-(hi-lo)*scale)/2-lo*scale;polys=[];at=np.asarray(atlas)
    light=np.array([-.3,.85,.4]);light/=np.linalg.norm(light)
    for p in parts:
        vertices=np.asarray(p.pos);uv=np.asarray(p.uv);normal=np.asarray(p.nrm)
        pr=np.stack([vertices@right,-vertices@up],1)*scale+offset;depth=vertices@eye
        for q in p.tris:
            idx=np.array(q);n=normal[idx].mean(0)
            if n@eye<-.1:continue
            u,v=uv[idx].mean(0);col=at[min(2047,int(v*2048)),min(2047,int(u*2048))].astype(float)
            if col[3]<128:continue
            col[:3]*=.55+.45*max(0,n@light)
            polys.append((depth[idx].mean(),pr[idx],tuple(col.clip(0,255).astype(int))))
    for _,ps,col in sorted(polys,key=lambda p:p[0]):d.polygon([tuple(v) for v in ps],fill=col)
    return im.resize((256,256),Image.Resampling.LANCZOS)
def resources(atlas,geo):
    for item,model in [('launcher','launcher'),('missile','missile'),('designator','remote')]:
        save(icon(geo[model],atlas['missile' if item=='missile' else 'launcher']),f'textures/item/{item}.png')
        js(A/f'models/item/{item}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'topol:item/{item}'}})
    # An actual 3D item rather than a flat handheld icon. Original texture material tiles.
    save(tex.paint(521,'#43513a').resize((256,256)), 'textures/item/remote_case.png')
    save(tex.metal(522,'#242924').resize((256,256)), 'textures/item/remote_dark.png')
    lens=Image.new('RGBA',(256,256),'#0b221b');ld=ImageDraw.Draw(lens)
    for rad in range(120,0,-1):ld.ellipse((128-rad,128-rad,128+rad,128+rad),fill=(20,50+int((120-rad)*.8),60,255))
    save(lens,'textures/item/remote_lens.png')
    def cuboid(a,b,texture):
        return {'from':a,'to':b,'faces':{face:{'texture':'#'+texture,'uv':[0,0,16,16]} for face in ['north','south','east','west','up','down']}}
    elements=[cuboid([4,8,3],[12,12,13],'case'),cuboid([5,12,4],[11,15,11],'case'),
              cuboid([6,2,4],[10,8,8],'dark'),cuboid([6,12,11],[10,15,15],'dark'),
              cuboid([6.3,12.3,15],[9.7,14.7,15.1],'lens'),cuboid([4.2,12,4],[4.5,19,4.3],'dark')]
    js(A/'models/item/designator.json',{'textures':{'case':'topol:item/remote_case','dark':'topol:item/remote_dark','lens':'topol:item/remote_lens','particle':'topol:item/remote_case'},
       'elements':elements,'display':{
       'firstperson_righthand':{'rotation':[0,-90,0],'translation':[1,1,-1],'scale':[.8,.8,.8]},
       'firstperson_lefthand':{'rotation':[0,90,0],'translation':[1,1,-1],'scale':[.8,.8,.8]},
       'thirdperson_righthand':{'rotation':[0,90,0],'translation':[0,2,0],'scale':[.65,.65,.65]},
       'gui':{'rotation':[25,-135,0],'translation':[0,0,0],'scale':[.9,.9,.9]},
       'ground':{'translation':[0,3,0],'scale':[.4,.4,.4]}}})
    save(icon(geo['launcher'],atlas['launcher']).resize((128,128)),'icon.png')
    noise=tex.noise(256,183,2.1)
    save(tex.to_img(tex.lerp(tex.rgb('#27221c'),tex.rgb('#64563e'),noise[...,None])),'textures/block/scorched_earth.png')
    js(A/'blockstates/scorched_earth.json',{'variants':{'':{'model':'topol:block/scorched_earth'}}})
    js(A/'models/block/scorched_earth.json',{'parent':'minecraft:block/cube_all','textures':{'all':'topol:block/scorched_earth'}})
    js(A/'models/item/scorched_earth.json',{'parent':'topol:block/scorched_earth'})
    js(R/'data/minecraft/tags/block/mineable/shovel.json',{'replace':False,'values':['topol:scorched_earth']})
    js(R/'data/topol/loot_table/blocks/scorched_earth.json',{'type':'minecraft:block','pools':[{'rolls':1,
       'entries':[{'type':'minecraft:item','name':'topol:scorched_earth'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for name,pattern,keys in [('launcher',[' I ','IPI','BBB'],{'I':'iron_ingot','P':'piston','B':'black_concrete'}),
                               ('missile',[' I ','IRI',' G '],{'I':'iron_ingot','R':'redstone','G':'gunpowder'}),
                               ('designator',[' GI',' RR'],{'G':'glass','I':'iron_ingot','R':'redstone'})]:
        js(R/f'data/topol/recipe/{name}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,
            'key':{key:{'item':'minecraft:'+value} for key,value in keys.items()},'result':{'id':'topol:'+name,'count':1}})
    laser=np.zeros((64,64,3));x=np.linspace(-1,1,64);laser[:]=np.exp(-x*x*20)[None,:,None]
    save(tex.to_img(laser),'textures/fx/laser.png')
def audio():
    sr=44100;rng=np.random.default_rng(9821)
    (A/'sounds').mkdir(exist_ok=True)
    for name in SOUNDS:
        duration={'blast_near':2.2,'blast_far':3.2,'roar_near':.7,'roar_far':1.0,'ignition':.7,'sonic_boom':.7,
                  'eject':.4,'cap':.2,'clunk':.25,'ui_beep':.1,'ui_confirm':.18,'ui_error':.22,'earth':.2}[name]
        t=np.arange(int(sr*duration))/sr;n=rng.standard_normal(len(t))
        def band(a,b):return sosfilt(butter(3,[a,b],btype='bandpass',fs=sr,output='sos'),n)
        low,mid,high=band(40,220),band(200,2400),band(2000,10000)
        if name.startswith('blast'):
            a=(low*1.5+mid*.8+high*.22*np.exp(-t*5))*np.exp(-t/ .48)
            a+=np.sin(2*np.pi*64*t)*np.exp(-t/.065)*.7
            for delay,gain in [(.12,.25),(.3,.17),(.7,.08)]:
                k=int(sr*delay);a[k:]+=a[:-k].copy()*gain
            if name.endswith('far'):a=sosfilt(butter(3,700,fs=sr,output='sos'),a)
        elif name.startswith('roar'):a=(low+mid*.3+high*.07)*np.sin(np.pi*t/duration)**.6
        elif name.startswith('ui_'):
            freq=220 if name=='ui_error' else 920 if name=='ui_confirm' else 700
            a=np.sin(2*np.pi*freq*t)*.3
        else:a=(low+.5*mid+.1*high)*np.exp(-t/(duration*.23))
        fade=min(int(.008*sr),len(a)//8);a[:fade]*=np.linspace(0,1,fade);a[-fade:]*=np.linspace(1,0,fade)
        a=np.tanh(a);a=a/max(.01,np.abs(a).max())*.85
        with tempfile.TemporaryDirectory() as tmp:
            wav=Path(tmp)/'sound.wav'
            with wave.open(str(wav),'wb') as w:w.setparams((1,2,sr,0,'NONE',''));w.writeframes((a*32767).astype('<i2').tobytes())
            subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','5',str(A/f'sounds/{name}.ogg')],check=True)
    js(A/'sounds.json',{name:{'subtitle':'subtitles.topol.'+name,'sounds':[{'name':'topol:'+name}]} for name in SOUNDS})
def language():
    en={'item.topol.launcher':'Solo Silo container','entity.topol.launcher':'Solo Silo container','item.topol.missile':'Conventional game rocket',
        'item.topol.designator':'Silo target designator','block.topol.scorched_earth':'Scorched earth',
        'silo.owner':'Only the owner or an operator can use this silo.','silo.wait':'Already loaded, or wait for the launch to finish.',
        'silo.loaded':'Round loaded. Stand clear and use the designator.','silo.bound':'Designator linked. Aim and HOLD use for half a second to fire.',
        'silo.hint':'Use a missile to reload, or a designator to bind. Sneak + hit to pack up.',
        'silo.unbound':'Bind this designator by using it on a silo.','silo.unavailable':'Silo unavailable: it must be loaded in the same dimension and owned by you.',
        'silo.empty':'Silo is empty. Load a rocket first.','silo.inflight':'This silo already has a rocket in flight.',
        'silo.limit':'Active silo/flight limit reached.','silo.roof':'Clear the space above the silo.',
        'silo.space':'A level 3 x 3 pad and clear overhead space are required.',
        'silo.hud.hold':'HOLD USE TO AIM AND FIRE','silo.hud.guiding':'GUIDING / RELEASE TO KEEP LAST TARGET',
        'silo.hud.lock':'VALID TARGET / LAUNCHING','silo.hud.no_target':'NO VALID TARGET','silo.hud.range':'%s blocks',
        'silo.hud.spent':'SHOT COMPLETE / RELEASE AND RELOAD'}
    ru={'item.topol.launcher':'Контейнер Solo Silo','entity.topol.launcher':'Контейнер Solo Silo','item.topol.missile':'Обычная игровая ракета',
        'item.topol.designator':'Целеуказатель пусковой','block.topol.scorched_earth':'Выжженная земля',
        'silo.owner':'Пусковой управляет владелец или оператор.','silo.wait':'Уже заряжено, либо дождись завершения запуска.',
        'silo.loaded':'Ракета заряжена. Отойди и используй целеуказатель.','silo.bound':'Пульт привязан. Наведи и УДЕРЖИВАЙ ПКМ полсекунды для пуска.',
        'silo.hint':'Ракета: зарядить. Пульт: привязать. Приседание + удар: забрать.',
        'silo.unbound':'Привяжи пульт: используй его на контейнере.','silo.unavailable':'Пусковая недоступна: она должна быть загружена в том же измерении и принадлежать тебе.',
        'silo.empty':'Контейнер пуст. Сначала заряди ракету.','silo.inflight':'Ракета этого контейнера ещё в полёте.',
        'silo.limit':'Достигнут лимит контейнеров или полётов.','silo.roof':'Освободи место над пусковой.',
        'silo.space':'Нужна ровная площадка 3 x 3 и свободное место сверху.',
        'silo.hud.hold':'УДЕРЖИВАЙ ПКМ: НАВЕДЕНИЕ И ПУСК','silo.hud.guiding':'НАВЕДЕНИЕ / ОТПУСТИ ДЛЯ ФИКСАЦИИ ЦЕЛИ',
        'silo.hud.lock':'ЦЕЛЬ ПРИНЯТА / ПУСК','silo.hud.no_target':'НЕТ ДОПУСТИМОЙ ЦЕЛИ','silo.hud.range':'%s блоков',
        'silo.hud.spent':'ВЫСТРЕЛ ЗАВЕРШЁН / ОТПУСТИ ПКМ И ПЕРЕЗАРЯДИ'}
    names=['Latch','Lid release','Launch','Ignition','Rocket pass','Distant rocket','Crack','Impact','Distant impact','Beep','Confirmed','Error','Ground']
    rus=['Фиксатор','Сброс крышки','Пуск','Зажигание','Пролёт ракеты','Далёкая ракета','Хлопок','Взрыв','Далёкий взрыв','Сигнал','Подтверждение','Ошибка','Грунт']
    for n,e,r in zip(SOUNDS,names,rus):en['subtitles.topol.'+n]=e;ru['subtitles.topol.'+n]=r
    js(A/'lang/en_us.json',en);js(A/'lang/ru_ru.json',ru)
if __name__=='__main__':
    atlas=atlases();geo=geometry();resources(atlas,geo);audio();language()
    # Rebuild all used FX so this generator remains standalone.
    for name,fn in [('smoke',fxtex.smoke_atlas),('fire',fxtex.fire_atlas),('glow',fxtex.glow),('plume',fxtex.plume),
                    ('spark',fxtex.spark),('scorch',fxtex.scorch),('debris',fxtex.debris_tex)]:
        save(fn(),f'textures/fx/{name}.png')
    print('Original compact-silo assets complete.',flush=True)
