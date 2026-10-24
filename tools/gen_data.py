"""Item models, block/data JSON, original synthesized audio and language files for Solo Silo.
Run after tools/gen_models.py and tools/gen_fx.py. Generated files ship in the ZIP; Gradle never needs Python.
"""
from pathlib import Path
import sys,json,math,subprocess,wave,tempfile
import numpy as np
from PIL import Image
from scipy.signal import butter,sosfilt
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools/assets'))
import tex
A=ROOT/'src/main/resources/assets/topol'
R=ROOT/'src/main/resources'
SOUNDS=['clunk','cap','eject','ignition','roar_near','roar_far','sonic_boom','blast_near','blast_far','ui_beep','ui_confirm','ui_error','earth']
def js(path,obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def save(image,path):
    p=A/path;p.parent.mkdir(parents=True,exist_ok=True);image.save(p,optimize=True)
def resources():
    for item in ['launcher','missile']:
        js(A/f'models/item/{item}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'topol:item/{item}'}})
    # 3D mesh item drawn by DesignatorRenderer. Lens faces -Z in item space. Left-hand views mirror these.
    js(A/'models/item/designator.json',{'parent':'minecraft:builtin/entity','gui_light':'front',
       'textures':{'particle':'topol:item/missile'},'display':{
       'firstperson_righthand':{'rotation':[0,0,0],'translation':[-3,2.5,-1],'scale':[.62,.62,.62]},
       'thirdperson_righthand':{'rotation':[90,0,0],'translation':[0,2,1],'scale':[.5,.5,.5]},
       'gui':{'rotation':[25,125,0],'translation':[0,0,0],'scale':[1.15,1.15,1.15]},
       'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.45,.45,.45]},
       'fixed':{'rotation':[0,90,0],'translation':[0,0,0],'scale':[1,1,1]},
       'head':{'rotation':[0,0,0],'translation':[0,6,0],'scale':[.8,.8,.8]}}})
    noise=tex.noise(256,183,2.1)
    save(tex.to_img(tex.lerp(tex.rgb('#27221c'),tex.rgb('#64563e'),noise[...,None])),'textures/block/scorched_earth.png')
    js(A/'blockstates/scorched_earth.json',{'variants':{'':{'model':'topol:block/scorched_earth'}}})
    js(A/'models/block/scorched_earth.json',{'parent':'minecraft:block/cube_all','textures':{'all':'topol:block/scorched_earth'}})
    js(A/'models/item/scorched_earth.json',{'parent':'topol:block/scorched_earth'})
    js(R/'data/minecraft/tags/block/mineable/shovel.json',{'replace':False,'values':['topol:scorched_earth']})
    js(R/'data/topol/loot_table/blocks/scorched_earth.json',{'type':'minecraft:block','pools':[{'rolls':1,
       'entries':[{'type':'minecraft:item','name':'topol:scorched_earth'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for name,pattern,keys in [('launcher',['IPI','ICI','BBB'],{'I':'iron_ingot','P':'iron_trapdoor','C':'piston','B':'smooth_stone'}),
                               ('missile',[' I ','IRI','IGI'],{'I':'iron_ingot','R':'redstone','G':'gunpowder'}),
                               ('designator',['GI ','RR '],{'G':'glass_pane','I':'iron_ingot','R':'redstone'})]:
        js(R/f'data/topol/recipe/{name}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,
            'key':{key:{'item':'minecraft:'+value} for key,value in keys.items()},'result':{'id':'topol:'+name,'count':1}})
    laser=np.zeros((64,64,3));x=np.linspace(-1,1,64);laser[:]=np.exp(-x*x*20)[None,:,None]
    save(tex.to_img(laser),'textures/fx/laser.png')
    for name in ['smoke','fire','glow','plume','debris','scorch']:
        (A/f'textures/fx/{name}.png.mcmeta').write_text(json.dumps({'texture':{'blur':True}})+'\n')
def audio():
    """Original synthesized sounds (no vanilla or third-party audio): mono 44.1 kHz Ogg Vorbis."""
    sr=44100;rng=np.random.default_rng(9821)
    (A/'sounds').mkdir(exist_ok=True)
    def T(d):return np.arange(int(sr*d))/sr
    def noise(n):return rng.standard_normal(n)
    def bp(x,a,b,o=3):return sosfilt(butter(o,[a,b],btype='bandpass',fs=sr,output='sos'),x)
    def lp(x,f,o=3):return sosfilt(butter(o,f,fs=sr,output='sos'),x)
    def hp(x,f,o=3):return sosfilt(butter(o,f,btype='highpass',fs=sr,output='sos'),x)
    def env(t,att,dec):return np.minimum(1,t/max(att,1e-4))*np.exp(-np.maximum(0,t-att)/dec)
    def ring(t,freqs,dec):return sum(np.sin(2*np.pi*f*t+rng.uniform(0,6))*np.exp(-t/(dec*(1+i*.4)))/(1+i) for i,f in enumerate(freqs))
    def echo(x,taps):
        y=x.copy()
        for d,g in taps:
            k=int(sr*d)
            if k<len(y):y[k:]+=lp(x[:-k],2500)*g
        return y
    def sweep(x,t,f0,f1,width=.6,steps=24):
        """crude time-varying band-pass: cross-faded fixed bands"""
        out=np.zeros_like(x);n=len(x);w=np.hanning(2*n//steps+2)
        for i in range(steps):
            c=f0*(f1/f0)**(i/(steps-1));seg=bp(x,c*(1-width/2),c*(1+width/2),2)
            a=max(0,i*n//steps-n//steps//2);b=min(n,a+len(w));out[a:b]+=seg[a:b]*w[:b-a]
        return out
    def crackle(t,rate,dec=.002):
        x=np.zeros(len(t));k=rng.random(len(t))<rate/sr
        x[k]=rng.uniform(-1,1,k.sum());return hp(lp(x,7000),300)
    def make(name):
        if name=='clunk':
            t=T(.45);return ring(t,[180,433,707,1130],.07)*.6+lp(noise(len(t)),900)*env(t,.001,.03)*1.2+np.sin(2*np.pi*70*t)*env(t,.002,.05)
        if name=='cap':
            t=T(1.5);hiss=bp(noise(len(t)),1800,6500)*(np.sin(np.pi*np.clip(t/1.3,0,1))**.7)*.5
            motor=(np.sin(2*np.pi*(85+25*t)*t)+.4*np.sin(2*np.pi*(170+50*t)*t))*np.sin(np.pi*np.clip(t/1.35,0,1))*.35
            tail=np.zeros(len(t));k=int(1.3*sr);tt=t[:len(t)-k];tail[k:]=ring(tt,[210,520,860],.06)*.7+lp(noise(len(tt)),700)*env(tt,.001,.03)
            return hiss+motor+tail
        if name=='eject':
            t=T(.8);return lp(noise(len(t)),350)*env(t,.01,.15)*1.6+bp(noise(len(t)),400,2500)*env(t,.005,.08)*.6
        if name=='ignition':
            t=T(3.2);e=np.minimum(1,t/.07)*np.clip((3.2-t)/1.6,0,1)**1.3
            roar=lp(noise(len(t)),180)*2.2+bp(noise(len(t)),150,1500)*.9+bp(noise(len(t)),1500,7000)*.25
            return (roar+crackle(t,900)*.9)*e+np.sin(2*np.pi*48*t)*env(t,.01,.25)*.8
        if name=='roar_near':
            t=T(1.6);x=noise(len(t))
            return (sweep(x,t,1400,260)*2.2+lp(x,200)*1.2)*np.sin(np.pi*t/1.6)**1.5+crackle(t,300)*.4*np.sin(np.pi*t/1.6)
        if name=='roar_far':
            t=T(2.2);return lp(noise(len(t)),260)*np.sin(np.pi*t/2.2)**1.2*1.8
        if name=='sonic_boom':
            t=T(.9);x=np.zeros(len(t))
            for at,sg in [(.0,1),(.11,-1)]:
                k=int(at*sr);x[k:k+60]+=sg*np.linspace(1,0,60)
            x=lp(x,3500)*40+lp(noise(len(t)),400)*env(t,.002,.12)*.6
            return echo(x,[(.18,.3),(.42,.15)])
        if name.startswith('blast'):
            far=name.endswith('far');t=T(4.5 if far else 4.0)
            crack=hp(noise(len(t)),800)*env(t,.0005,.012)*2.5
            boom=lp(noise(len(t)),110,4)*env(t,.004,.55)*5+np.sin(2*np.pi*(38+30*np.exp(-t*6))*t)*env(t,.003,.35)*1.4
            body=bp(noise(len(t)),120,1600)*env(t,.003,.25)*1.4
            rumble=lp(noise(len(t)),220)*np.clip(t/.3,0,1)*np.exp(-t/1.6)*1.3
            debris=np.zeros(len(t))
            for _ in range(70):
                at=rng.uniform(.5,3.4);k=int(at*sr);n=int(.05*sr)
                if k+n<len(t):debris[k:k+n]+=bp(noise(n),300,3000)*np.exp(-np.arange(n)/sr/.012)*rng.uniform(.1,.5)
            x=crack+boom+body+rumble+debris*.8
            x=echo(x,[(.14,.3),(.35,.22),(.8,.12),(1.5,.06)])
            if far:x=lp(np.concatenate([np.zeros(int(.05*sr)),x])[:len(t)],600,4)*1.3
            return x
        if name.startswith('ui_'):
            d={'ui_beep':.1,'ui_confirm':.18,'ui_error':.22}[name];t=T(d)
            f=220 if name=='ui_error' else 920 if name=='ui_confirm' else 700
            return np.sin(2*np.pi*f*t)*.3
        if name=='earth':
            t=T(.35);return lp(noise(len(t)),600)*env(t,.002,.05)*1.5+bp(noise(len(t)),900,4000)*env(t,.002,.02)*.4
        raise KeyError(name)
    for name in SOUNDS:
        a=make(name)
        fade=min(int(.006*sr),len(a)//8);a[:fade]*=np.linspace(0,1,fade);a[-fade:]*=np.linspace(1,0,fade)
        a=np.tanh(a/max(.01,np.abs(a).max())*1.6);a=a/max(.01,np.abs(a).max())*.89
        with tempfile.TemporaryDirectory() as tmp:
            wav=Path(tmp)/'sound.wav'
            with wave.open(str(wav),'wb') as w:w.setparams((1,2,sr,0,'NONE',''));w.writeframes((a*32767).astype('<i2').tobytes())
            subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-ar','44100','-ac','1','-c:a','libvorbis','-q:a','6',str(A/f'sounds/{name}.ogg')],check=True)
    js(A/'sounds.json',{name:{'subtitle':'subtitles.topol.'+name,'sounds':[{'name':'topol:'+name}]} for name in SOUNDS})
def language():
    en={'item.topol.launcher':'Solo Silo launch shaft','entity.topol.launcher':'Solo Silo launch shaft','item.topol.missile':'Cruise missile (conventional)',
        'item.topol.designator':'Perst-4 laser designator','block.topol.scorched_earth':'Scorched earth',
        'silo.owner':'Only the owner or an operator can use this silo.','silo.wait':'Already loaded, or wait for the launch to finish.',
        'silo.loaded':'Round loaded. Stand clear and use the designator.','silo.bound':'Designator linked. Aim and HOLD use: half a second to lock, keep holding to steer the missile.',
        'silo.hint':'Use a missile to reload, or a designator to bind. Sneak + hit to pack up.',
        'silo.unbound':'Bind this designator by using it on a silo.','silo.unavailable':'Silo unavailable: it must be loaded in the same dimension and owned by you.',
        'silo.empty':'Silo is empty. Load a rocket first.','silo.inflight':'This silo already has a rocket in flight.',
        'silo.limit':'Active silo/flight limit reached.','silo.roof':'Clear the space above the silo.',
        'silo.space':'Needs a solid 3 x 3 footing, open sky above and a diggable 7-block column below (no fluids or containers).',
        'silo.hud.hold':'HOLD USE TO AIM AND FIRE','silo.hud.guiding':'GUIDING / RELEASE TO KEEP LAST TARGET',
        'silo.hud.lock':'VALID TARGET / LAUNCHING','silo.hud.no_target':'NO VALID TARGET','silo.hud.range':'%s blocks',
        'silo.hud.spent':'SHOT COMPLETE / RELEASE AND RELOAD'}
    ru={'item.topol.launcher':'Пусковая шахта Solo Silo','entity.topol.launcher':'Пусковая шахта Solo Silo','item.topol.missile':'Крылатая ракета (обычная БЧ)',
        'item.topol.designator':'Лазерный целеуказатель «Перст-4»','block.topol.scorched_earth':'Выжженная земля',
        'silo.owner':'Пусковой управляет владелец или оператор.','silo.wait':'Уже заряжено, либо дождись завершения запуска.',
        'silo.loaded':'Ракета заряжена. Отойди и используй целеуказатель.','silo.bound':'Целеуказатель привязан. Наведи и УДЕРЖИВАЙ ПКМ: полсекунды до захвата, дальше держи луч для наведения ракеты.',
        'silo.hint':'Ракета: зарядить. Пульт: привязать. Приседание + удар: забрать.',
        'silo.unbound':'Привяжи пульт: используй его на контейнере.','silo.unavailable':'Пусковая недоступна: она должна быть загружена в том же измерении и принадлежать тебе.',
        'silo.empty':'Контейнер пуст. Сначала заряди ракету.','silo.inflight':'Ракета этого контейнера ещё в полёте.',
        'silo.limit':'Достигнут лимит контейнеров или полётов.','silo.roof':'Освободи место над пусковой.',
        'silo.space':'Нужна твёрдая площадка 3 x 3, открытое небо и копаемый столб 7 блоков вниз (без жидкостей и сундуков).',
        'silo.hud.hold':'УДЕРЖИВАЙ ПКМ: НАВЕДЕНИЕ И ПУСК','silo.hud.guiding':'НАВЕДЕНИЕ / ОТПУСТИ ДЛЯ ФИКСАЦИИ ЦЕЛИ',
        'silo.hud.lock':'ЦЕЛЬ ПРИНЯТА / ПУСК','silo.hud.no_target':'НЕТ ДОПУСТИМОЙ ЦЕЛИ','silo.hud.range':'%s блоков',
        'silo.hud.spent':'ВЫСТРЕЛ ЗАВЕРШЁН / ОТПУСТИ ПКМ И ПЕРЕЗАРЯДИ'}
    names=['Latch','Hatch hydraulics','Launch','Booster ignition','Rocket pass','Distant rocket','Crack','Impact','Distant impact','Beep','Confirmed','Error','Ground']
    rus=['Фиксатор','Гидравлика люка','Пуск','Запуск ускорителя','Пролёт ракеты','Далёкая ракета','Хлопок','Взрыв','Далёкий взрыв','Сигнал','Подтверждение','Ошибка','Грунт']
    for n,e,r in zip(SOUNDS,names,rus):en['subtitles.topol.'+n]=e;ru['subtitles.topol.'+n]=r
    js(A/'lang/en_us.json',en);js(A/'lang/ru_ru.json',ru)
if __name__=='__main__':
    resources();audio();language()
    print('Data, audio and language written.',flush=True)
