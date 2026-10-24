#!/usr/bin/env python3
"""Reproducible original artwork and foley. Not required to build the mod: generated files ship in resources."""
from pathlib import Path
import sys, json, math, subprocess, wave, tempfile
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from scipy.signal import butter, sosfilt

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools/assets'))
import mesh, models, tex, fxtex

A = ROOT / 'src/main/resources/assets/topol'
R = ROOT / 'src/main/resources'

def save(img, path):
    path = A / path
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)

def js(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def markings(missile=False):
    s = tex.INNER
    im = Image.new('RGBA', (s, s))
    if missile:
        decal = tex.text_decal(['Т', 'О', 'П', 'О', 'Л', 'Ь'], s//4, s, size=54)
        im.alpha_composite(decal, (0, 0))
    else:
        for i, lines in enumerate([['ТОПОЛЬ / 01'], ['НЕ СТОЯТЬ ПОД СТРЕЛОЙ'], ['УЧЕБНЫЙ КОМПЛЕКС'], ['ОСТОРОЖНО']]):
            decal = tex.text_decal(lines, s, s//4, size=28 if i==1 else 42)
            im.alpha_composite(decal, (0, i*(s//4)))
    return im

def atlases():
    L = [tex.paint(1), tex.paint(2, '#46502f'), tex.paint(3, '#29301f', wear=.5), tex.metal(4),
         tex.chrome(5), tex.tread(6), tex.sidewall(7), tex.grille(8),
         tex.glass(9), tex.paint(10, '#4c5836', panel_px=248), tex.rubber(11), tex.stripes(12),
         tex.cab_front(13), markings(), tex.lights_tile(), tex.cap_tile(14)]
    M = [tex.missile_paint(21, '#a3a895'), tex.missile_paint(22, '#87927d'), tex.missile_paint(23, '#393f32'), tex.metal(24),
         tex.nozzle_inner(25), tex.nozzle_outer(26), tex.grid_metal(27), tex.insulation(28),
         tex.heatshield(29), tex.heatshield(30), markings(True), tex.band_tile(31),
         tex.missile_paint(32, '#dbd9cb'), tex.metal(33), tex.rubber(34), tex.stripes(35)]
    out = {}
    for name, tiles in [('launcher', L), ('missile', M)]:
        atlas = Image.new('RGBA', (2048, 2048))
        for i, tile in enumerate(tiles):
            padded = tex.pad_clamp(tile) if i in (12, 13, 14) or (name=='missile' and i==10) else tex.pad_tile(tile)
            atlas.paste(padded, ((i%4)*512, (i//4)*512))
        for n in [2048, 1024, 512]:
            save(atlas.resize((n, n), Image.Resampling.LANCZOS), f'textures/entity/{name}_{n}.png')
        out[name] = atlas
    return out

def geometry():
    out = {}
    (A / 'models/tmsh').mkdir(parents=True, exist_ok=True)
    for name, fn in [('launcher', models.tel), ('missile', models.missile), ('rv', models.reentry_vehicle)]:
        for lod in range(2):
            p, anchors = fn(lod)
            mesh.write_tmsh(A/f'models/tmsh/{name}_{lod}.tmsh', p, anchors)
            if lod == 0: out[name] = p
            print(name, lod, sum(len(x.tris) for x in p), 'quads', flush=True)
    # One source of truth for pivots. Generated, checked in.
    g = models.G
    java = '''package com.hurma.topol;
/** Generated from tools/assets/models.py. Units: blocks. +Z front, +Y up. */
public final class LauncherGeometry {
    private LauncherGeometry() {}
'''
    for key in ['LENGTH_FRONT','LENGTH_REAR','HALF_WIDTH','TPK_Y','TPK_Z0','TPK_Z1','HINGE_Y','HINGE_Z','MISSILE_BASE_Z','JACK_REST_Y']:
        java += f'    public static final double {key} = {g[key]};\n'
    for key in ['RAM_A','RAM_B']:
        java += f'    public static final double[] {key} = {{'+', '.join(str(v) for v in g[key])+'};\n'
    java += '}\n'
    path = ROOT/'src/main/java/com/hurma/topol/LauncherGeometry.java'
    path.write_text(java, encoding='utf-8')
    return out

def fx():
    for name, fn in [('smoke', fxtex.smoke_atlas), ('fire', fxtex.fire_atlas), ('glow', fxtex.glow), ('flare', fxtex.flare),
                     ('plume', fxtex.plume), ('spark', fxtex.spark), ('ring', fxtex.ring), ('scorch', fxtex.scorch),
                     ('debris', fxtex.debris_tex)]:
        save(fn(), f'textures/fx/{name}.png')
        print('fx', name, flush=True)

def gui():
    # Painted instrument case, glass inset, fasteners. Text is drawn by the game for both languages.
    W, H = 1200, 880
    noise = tex.noise((H, W), 451, 1.6)
    base = np.array([39, 47, 35])[None,None,:] * (.83 + .30*noise[...,None])
    im = Image.fromarray(base.astype('uint8'), 'RGB').convert('RGBA')
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((3,3,W-4,H-4), 28, outline='#768269', width=5)
    d.rounded_rectangle((19,19,W-20,H-20), 20, outline='#171e15', width=10)
    d.rounded_rectangle((40,52,W-40,214), 12, fill='#101c17', outline='#66866c', width=3)
    for y in range(57,209,5):
        d.line((46,y,W-46,y),fill=(25,43,32,255),width=1)
    d.rounded_rectangle((40,240,W-40,610),10,fill='#151e17',outline='#4a5741',width=3)
    d.rounded_rectangle((40,632,W-40,H-40),10,fill='#212a1e',outline='#4a5741',width=3)
    for x in [21,W-22]:
        for y in [24,H//2,H-25]:
            d.ellipse((x-9,y-9,x+9,y+9),fill='#78816e',outline='#10160e',width=2)
            d.line((x-5,y+5,x+5,y-5),fill='#262e20',width=3)
    save(im, 'textures/gui/console.png')
    icon=Image.new('RGBA',(128,128),(24,33,23,255))
    di=ImageDraw.Draw(icon)
    di.rounded_rectangle((5,5,122,122),12,outline='#9db075',width=5)
    di.polygon([(64,15),(78,48),(75,98),(53,98),(50,48)],fill='#dadac4')
    di.polygon([(53,86),(34,111),(53,102)],fill='#808f69')
    di.polygon([(75,86),(94,111),(75,102)],fill='#808f69')
    di.polygon([(57,105),(64,124),(71,105)],fill='#f5a941')
    save(icon,'icon.png')

def item_icon(parts, atlas, kind):
    """Small isometric software render of the actual mesh, painter-sorted triangles. No external art."""
    W = 768
    im = Image.new('RGBA',(W,W))
    d = ImageDraw.Draw(im)
    verts=[]
    if kind=='launcher':
        parts=[p for p in parts if p.name not in ('ram_tube','ram_rod')]
        az,el=math.radians(48),math.radians(25)
    else:
        az,el=math.radians(35),math.radians(12)
    right=np.array([math.cos(az),0,-math.sin(az)])
    up=np.array([-math.sin(az)*math.sin(el),math.cos(el),-math.cos(az)*math.sin(el)])
    eye=np.cross(right,up)
    for p in parts:
        pos=np.array(p.pos)
        pos=pos @ np.array([[1,0,0],[0,1,0],[0,0,1]])
        q=np.stack([pos@right,-pos@up,pos@eye],axis=1)
        verts.append((p,q))
    allv=np.concatenate([q for _,q in verts])
    lo,hi=allv[:,:2].min(0),allv[:,:2].max(0)
    sc=(W-50)/max(hi-lo)
    off=(W-(hi-lo)*sc)/2-lo*sc
    a=np.asarray(atlas)
    polys=[]
    light=np.array([-.3,.85,.45])
    light/=np.linalg.norm(light)
    for p,q in verts:
        normals=np.array(p.nrm); uvs=np.array(p.uv); shades=np.array(p.col)
        for ids in p.tris:
            idx=np.array(ids)
            n=normals[idx].mean(0)
            if n@eye < -.05: continue
            uv=uvs[idx].mean(0)
            col=a[min(2047,int(uv[1]*2048)),min(2047,int(uv[0]*2048))].copy().astype(float)
            if col[3]<128: continue
            shade=np.mean(shades[idx])*(.55+.45*max(0,n@light))
            col[:3]*=shade
            pts=q[idx,:2]*sc+off
            polys.append((q[idx,2].mean(),pts,tuple(col.clip(0,255).astype(int))))
    for _,pts,col in sorted(polys,key=lambda a:a[0]):
        d.polygon([tuple(x) for x in pts],fill=col)
    return im.resize((256,256),Image.Resampling.LANCZOS)

def blocks():
    n=256
    nz=tex.noise(n,183,2.1)
    fine=tex.band(n,184,32,110)
    for name,lo,hi in [('scorched_earth','#1e1b16','#534737'),('trinitite','#142b26','#59776b')]:
        col=tex.lerp(tex.rgb(lo),tex.rgb(hi),(nz*.8+fine*.2)[...,None])
        if name=='trinitite':
            col=tex.compose(col,[(np.maximum(0,(fine-.62)*3),tex.rgb('#a6b2a0'),.32)])
        save(tex.to_img(col),f'textures/block/{name}.png')
        js(A/f'blockstates/{name}.json',{'variants':{'':{'model':f'topol:block/{name}'}}})
        js(A/f'models/block/{name}.json',{'parent':'minecraft:block/cube_all','textures':{'all':f'topol:block/{name}'}})
        js(A/f'models/item/{name}.json',{'parent':f'topol:block/{name}'})
        js(R/f'data/topol/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,
           'entries':[{'type':'minecraft:item','name':f'topol:{name}'}],
           'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    js(R/'data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['topol:trinitite']})
    js(R/'data/minecraft/tags/block/mineable/shovel.json',{'replace':False,'values':['topol:scorched_earth']})

SOUNDS=['hydraulics','diesel','clunk','siren','cap','eject','ignition','roar_near','roar_far',
        'stage_sep','sonic_boom','blast_near','blast_far','ui_beep','ui_confirm','ui_error','earth']

def sounds():
    sr=44100
    rng=np.random.default_rng(2300)
    def filt(n,lo,hi):
        return sosfilt(butter(3,[lo,hi],btype='bandpass',fs=sr,output='sos'),n)
    def synth(name):
        seconds={'hydraulics':4,'diesel':4,'siren':6,'roar_near':4,'roar_far':4,
                 'blast_near':9,'blast_far':12,'sonic_boom':3,'ui_beep':.12,'ui_confirm':.25,
                 'ui_error':.3,'earth':.35}.get(name,2.5)
        t=np.arange(int(sr*seconds))/sr
        noise=rng.standard_normal(len(t))
        low=filt(noise,30,180); mid=filt(noise,120,1800); high=filt(noise,900,9000)
        loop=name in ['diesel','hydraulics','roar_near','roar_far']
        if name=='diesel':
            x=.5*low+.12*np.sin(2*np.pi*45*t)*(1+.3*np.sin(2*np.pi*12*t))+.06*mid
        elif name=='hydraulics':
            x=.3*mid+.18*np.sin(2*np.pi*(140*t+2*np.sin(2*np.pi*t)))+.2*low
        elif name=='siren':
            phase=2*np.pi*(460*t-50/(2*np.pi*.5)*np.cos(2*np.pi*.5*t))
            x=.35*np.sin(phase)+.10*np.sin(3*phase)
        elif name.startswith('roar'):
            x=(2*low+.45*mid+.06*high)*(1+.12*np.sin(2*np.pi*7*t))
            if name=='roar_far': x=filt(x,25,380)
        elif name.startswith('blast'):
            env=np.exp(-t/2.8)
            x=(2*low+.8*mid+.20*high*np.exp(-t/1.5))*env
            x+=np.sin(2*np.pi*(42*t-2*t*t))*np.exp(-t/.45)
            # Late reflections, generated echoes, not a vanilla sound.
            for delay,gain in [(0.24,.35),(.6,.25),(1.3,.2),(2.1,.1)]:
                k=int(sr*delay); x[k:]+=gain*x[:-k].copy()
            if name=='blast_far': x=filt(x,25,500)
        elif name in ['eject','ignition','cap','stage_sep','sonic_boom','clunk']:
            decay={'eject':.7,'ignition':1.2,'cap':.19,'stage_sep':.25,'sonic_boom':.8,'clunk':.15}[name]
            x=(1.5*low+.7*mid+.10*high)*np.exp(-t/decay)
            if name=='sonic_boom':
                k=int(.09*sr); x[k:]+=.75*x[:-k].copy()
            if name=='clunk':
                x+=.35*np.sin(2*np.pi*180*t)*np.exp(-t/.12)
        elif name=='earth':
            x=(mid+.6*low)*np.exp(-t/.08)
        else:
            freq={'ui_beep':780,'ui_confirm':1040,'ui_error':230}[name]
            x=.25*np.sin(2*np.pi*freq*t)
            if name=='ui_confirm': x+=.15*np.sin(2*np.pi*1560*t)
        if loop:
            # Make a continuous loop via a short equal-power overlap.
            k=int(sr*.12)
            f=np.linspace(0,1,k)
            x[:k]=x[:k]*f+x[-k:]*(1-f)
            x=x[:-k]
        else:
            fade=min(len(x)//4, int(.012*sr))
            x[:fade]*=np.linspace(0,1,fade)
            tail=min(len(x)//3,int(.15*sr))
            x[-tail:]*=np.linspace(1,0,tail)
        x=np.tanh(x)
        x=x/(max(.01,np.max(np.abs(x))))*.89
        return (x*32767).astype('<i2')
    (A/'sounds').mkdir(parents=True,exist_ok=True)
    for name in SOUNDS:
        with tempfile.TemporaryDirectory() as tmp:
            wav=Path(tmp)/'a.wav'
            with wave.open(str(wav),'wb') as f:
                f.setparams((1,2,sr,0,'NONE','not compressed'))
                f.writeframes(synth(name).tobytes())
            subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),
                            '-c:a','libvorbis','-q:a','5',str(A/f'sounds/{name}.ogg')],check=True)
        print('audio',name,flush=True)
    js(A/'sounds.json',{n:{'subtitle':f'subtitles.topol.{n}','sounds':[{'name':f'topol:{n}','stream':False}]} for n in SOUNDS})

def language():
    en={
        'entity.topol.launcher':'Topol mobile launcher',
        'item.topol.launcher':'Topol mobile launcher',
        'item.topol.missile':'Three-stage game missile',
        'block.topol.scorched_earth':'Scorched earth','block.topol.trinitite':'Fused glass',
        'screen.topol.title':'TOPOL / LAUNCH CONSOLE',
        'screen.topol.target':'TARGET COORDINATES','screen.topol.warhead':'PAYLOAD',
        'screen.topol.deploy':'DEPLOY','screen.topol.stow':'STOW','screen.topol.arm':'ARM',
        'screen.topol.fire':'CONFIRM LAUNCH','screen.topol.cancel':'CANCEL ARMING',
        'screen.topol.inert':'INERT / TRAINING','screen.topol.blast':'DESTRUCTIVE',
        'screen.topol.close':'CLOSE','screen.topol.range':'Range: %s blocks',
        'screen.topol.limits':'Allowed: %s to %s blocks',
        'screen.topol.loaded':'LOADED','screen.topol.empty':'EMPTY: USE A MISSILE ITEM',
        'screen.topol.confirm':'CONFIRM WITHIN 5 SECONDS. TARGET WILL BE DESTROYED.',
        'screen.topol.hint':'Sneak + hit to pack up while stowed.',
        'state.topol.0':'STOWED','state.topol.1':'DEPLOYING','state.topol.2':'READY',
        'state.topol.3':'LAUNCH SEQUENCE','state.topol.4':'EMPTY / DEPLOYED','state.topol.5':'STOWING',
        'message.topol.0':'Systems online.',
        'message.topol.1':'Armed. Confirm launch within 5 seconds.',
        'message.topol.2':'This launcher belongs to another player.',
        'message.topol.3':'Deploy the launcher and load a missile first.',
        'message.topol.4':'Target outside allowed range or world border.',
        'message.topol.5':'Launch confirmation expired. Arm again.',
        'message.topol.6':'Flight limit reached. Try again later.',
        'message.topol.7':'Stand closer to the console.',
        'message.topol.8':'Operation accepted.',
        'message.topol.9':'Need a clear, level 27 x 5 area and room above the canister.',
        'message.topol.10':'Loaded.',
        'message.topol.11':'Launcher must be stowed to reload or pick up.',
        'message.topol.12':'Launch path obstructed. Clear the space above the rear.',
        'message.topol.13':'Arming cancelled.',
        'message.topol.14':'Destruction is disabled by the server configuration.',
        'message.topol.15':'Too many launchers in this world.',
        'tooltip.topol.launcher':'Place on level ground. Right click for console.',
        'tooltip.topol.missile':'Use on a stowed launcher to load it.',
    }
    ru={
        'entity.topol.launcher':'Мобильная пусковая «Тополь»','item.topol.launcher':'Мобильная пусковая «Тополь»',
        'item.topol.missile':'Трёхступенчатая игровая ракета','block.topol.scorched_earth':'Выжженная земля',
        'block.topol.trinitite':'Спёкшееся стекло','screen.topol.title':'ТОПОЛЬ / ПУЛЬТ ПУСКА',
        'screen.topol.target':'КООРДИНАТЫ ЦЕЛИ','screen.topol.warhead':'НАГРУЗКА',
        'screen.topol.deploy':'РАЗВЕРНУТЬ','screen.topol.stow':'СВЕРНУТЬ','screen.topol.arm':'ПОДГОТОВИТЬ ПУСК',
        'screen.topol.fire':'ПОДТВЕРДИТЬ ПУСК','screen.topol.cancel':'ОТМЕНА ПОДГОТОВКИ',
        'screen.topol.inert':'УЧЕБНАЯ / БЕЗ ВЗРЫВА','screen.topol.blast':'РАЗРУШИТЕЛЬНАЯ',
        'screen.topol.close':'ЗАКРЫТЬ','screen.topol.range':'Дальность: %s блоков',
        'screen.topol.limits':'Допустимо: от %s до %s блоков',
        'screen.topol.loaded':'ЗАРЯЖЕНО','screen.topol.empty':'ПУСТО: ИСПОЛЬЗУЙ ПРЕДМЕТ РАКЕТЫ',
        'screen.topol.confirm':'ПОДТВЕРДИ ЗА 5 СЕКУНД. ЦЕЛЬ БУДЕТ РАЗРУШЕНА.',
        'screen.topol.hint':'Крадись + ударь, чтобы забрать свёрнутую пусковую.',
        'state.topol.0':'СВЁРНУТО','state.topol.1':'РАЗВЁРТЫВАНИЕ','state.topol.2':'ГОТОВО',
        'state.topol.3':'ПУСКОВАЯ ПОСЛЕДОВАТЕЛЬНОСТЬ','state.topol.4':'ПУСТО / РАЗВЁРНУТО','state.topol.5':'СВОРАЧИВАНИЕ',
        'message.topol.0':'Системы включены.','message.topol.1':'Подготовлено. Подтверди пуск за 5 секунд.',
        'message.topol.2':'Эта пусковая принадлежит другому игроку.',
        'message.topol.3':'Сначала разверни пусковую и заряди ракету.',
        'message.topol.4':'Цель вне допустимой дальности или границы мира.',
        'message.topol.5':'Подтверждение истекло. Подготовь пуск заново.',
        'message.topol.6':'Достигнут лимит ракет в полёте. Попробуй позже.',
        'message.topol.7':'Подойди ближе к пульту.','message.topol.8':'Команда принята.',
        'message.topol.9':'Нужна свободная ровная площадка 27 x 5 и место над контейнером.',
        'message.topol.10':'Ракета заряжена.','message.topol.11':'Для зарядки и упаковки сверни пусковую.',
        'message.topol.12':'Над задней частью препятствие. Освободи путь ракете.',
        'message.topol.13':'Подготовка отменена.','message.topol.14':'Разрушения отключены в конфигурации сервера.',
        'message.topol.15':'Достигнут лимит пусковых в этом мире.',
        'tooltip.topol.launcher':'Поставь на ровную площадку. ПКМ открывает пульт.',
        'tooltip.topol.missile':'Используй на свёрнутой пусковой для зарядки.',
    }
    ens=['Hydraulics','Auxiliary engine','Mechanical lock','Launch siren','Canister cap released','Cold launch',
         'Engine ignition','Engine roar','Distant engine','Stage separation','Sonic boom','Blast','Distant blast',
         'Console beep','Console confirmed','Console error','Scorched ground']
    rus=['Гидравлика','Вспомогательный двигатель','Фиксатор','Пусковая сирена','Сброс крышки','Холодный старт',
         'Зажигание двигателя','Рёв двигателя','Далёкий двигатель','Отделение ступени','Звуковой удар','Взрыв',
         'Далёкий взрыв','Сигнал пульта','Подтверждение пульта','Ошибка пульта','Выжженная земля']
    en.update({'hud.topol.flight':'TOPOL / %s','hud.topol.data':'H %sm | V %sm/s | T-%ss'})
    ru.update({'hud.topol.flight':'ТОПОЛЬ / %s','hud.topol.data':'H %sм | V %sм/с | T-%sс'})
    for name,e,r in [('tube','EJECTION','ВЫБРОС'),('rise','FREE RISE','СВОБОДНЫЙ ПОДЪЁМ'),
                     ('burn','POWERED FLIGHT','РАБОТА ДВИГАТЕЛЯ'),('separation','SEPARATION','РАЗДЕЛЕНИЕ'),
                     ('coast','COAST','СВОБОДНЫЙ ПОЛЁТ'),('reentry','FINAL DIVE','ВХОД / СНИЖЕНИЕ'),
                     ('impact','IMPACT','ПОПАДАНИЕ')]:
        en['phase.topol.'+name]=e;ru['phase.topol.'+name]=r
    for n,e,r in zip(SOUNDS,ens,rus):
        en[f'subtitles.topol.{n}']=e
        ru[f'subtitles.topol.{n}']=r
    js(A/'lang/en_us.json',en); js(A/'lang/ru_ru.json',ru)

def recipes():
    js(R/'data/topol/recipe/launcher.json',{'type':'minecraft:crafting_shaped',
        'pattern':['IPI','IRI','BBB'],'key':{'I':{'item':'minecraft:iron_block'},'P':{'item':'minecraft:piston'},
        'R':{'item':'minecraft:redstone_block'},'B':{'item':'minecraft:black_concrete'}},
        'result':{'id':'topol:launcher','count':1}})
    js(R/'data/topol/recipe/missile.json',{'type':'minecraft:crafting_shaped',
        'pattern':[' I ','IRI','IGI'],'key':{'I':{'item':'minecraft:iron_ingot'},'R':{'item':'minecraft:redstone_block'},
        'G':{'item':'minecraft:gunpowder'}},'result':{'id':'topol:missile','count':1}})

if __name__=='__main__':
    atlas=atlases()
    geo=geometry()
    fx(); gui(); blocks(); language(); recipes(); sounds()
    for kind in ['launcher','missile']:
        save(item_icon(geo[kind],atlas[kind],kind),f'textures/item/{kind}.png')
        js(A/f'models/item/{kind}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'topol:item/{kind}'}})
    js(A/'asset_manifest.json',{'generator':'tools/gen_assets.py','seeded':True,'author':'Original procedural assets for this project',
        'atlases':{'launcher':[2048,1024,512],'missile':[2048,1024,512]},
        'mesh_format':'TMSH 1, quads, metres','audio':'Original synthesized mono Ogg Vorbis','vanilla_effects':False})
    print('Assets generated.',flush=True)
