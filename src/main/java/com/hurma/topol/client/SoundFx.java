package com.hurma.topol.client;

import com.hurma.topol.Topol;
import com.hurma.topol.entity.LauncherEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Original sounds only. Distance gain and acoustic delay are separate from Minecraft's 16-block rolloff. */
public final class SoundFx {
    private record Scheduled(int due,String name,Vec3d pos,float gain,float pitch){}
    private record Frame(int tick,Vec3d pos,float power){}
    private static final ArrayList<Scheduled> QUEUE=new ArrayList<>();
    private static final Map<Long,Track> TRACKS=new HashMap<>();
    private static final ArrayList<MovingSoundInstance> LOOPS=new ArrayList<>();
    private static int clock;
    private SoundFx(){}
    private static Vec3d listener(){return MinecraftClient.getInstance().gameRenderer.getCamera().getPos();}
    public static void clear() {
        var manager=MinecraftClient.getInstance().getSoundManager();
        for(MovingSoundInstance s:LOOPS)manager.stop(s);
        LOOPS.clear();QUEUE.clear();TRACKS.clear();clock=0;
    }
    public static void ui(String name) {
        MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(Topol.id(name),SoundCategory.MASTER,
                .6f*Topol.CONFIG.soundVolume,1,SoundInstance.createRandom(),false,0,SoundInstance.AttenuationType.NONE,0,0,0,true));
    }
    public static void play(String name,Vec3d pos,float gain,boolean delay) {
        if(QUEUE.size()>=160)return;
        double d=listener().distanceTo(pos);
        if(d>Topol.CONFIG.effectsDistance*1.5)return;
        QUEUE.add(new Scheduled(clock+(delay?(int)Math.round(d/343*20):0),name,pos,gain,1));
    }
    public static void blast(Vec3d pos) {
        play(listener().distanceTo(pos)>220?"blast_far":"blast_near",pos,1,true);
    }
    public static void tick() {
        clock++;
        Iterator<Scheduled> it=QUEUE.iterator();
        while(it.hasNext()) {
            Scheduled s=it.next();if(s.due>clock)continue;
            double d=listener().distanceTo(s.pos);
            double reach=s.name.startsWith("blast")?1200:s.name.equals("sonic_boom")?600:s.name.equals("ignition")?450:100;
            float vol=(float)(s.gain/(1+d/reach))*Topol.CONFIG.soundVolume;
            if(vol>.003)MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(Topol.id(s.name),SoundCategory.BLOCKS,
                    vol,s.pitch,SoundInstance.createRandom(),false,0,SoundInstance.AttenuationType.NONE,
                    s.pos.x,s.pos.y,s.pos.z,false));
            it.remove();
        }
        LOOPS.removeIf(MovingSoundInstance::isDone);
        TRACKS.values().removeIf(t->clock-t.lastUpdate>1100 || t.started&&clock-t.lastPower>1100);
    }
    public static void flight(long id,Vec3d pos,boolean burning) {
        Track t=TRACKS.computeIfAbsent(id,k->new Track());
        t.frames[clock&1023]=new Frame(clock,pos,burning?1:0);
        t.latest=pos;t.lastUpdate=clock;
        if(burning)t.lastPower=clock;
        if(burning&&!t.started) {
            t.started=true;
            add(new Roar(t,false));add(new Roar(t,true));
        }
    }
    public static void machine(LauncherEntity launcher) {
        if(launcher==null)return;
        add(new Mechanical(launcher,false));add(new Mechanical(launcher,true));
    }
    private static void add(MovingSoundInstance sound){LOOPS.add(sound);MinecraftClient.getInstance().getSoundManager().play(sound);}
    private static final class Track {
        final Frame[] frames=new Frame[1024];
        Vec3d latest=Vec3d.ZERO;
        int lastUpdate,lastPower;
        boolean started;
        Frame sample(Vec3d listener) {
            int ago=Math.min(1023,(int)(listener.distanceTo(latest)/343*20));
            Frame f=frames[(clock-ago)&1023];
            if(f==null||clock-f.tick>1024)return null;
            ago=Math.min(1023,(int)(listener.distanceTo(f.pos)/343*20));
            f=frames[(clock-ago)&1023];
            return f!=null&&clock-f.tick<1024?f:null;
        }
    }
    private static final class Roar extends MovingSoundInstance {
        final Track track;final boolean far;
        Roar(Track track,boolean far) {
            super(Topol.sound(far?"roar_far":"roar_near"),SoundCategory.BLOCKS,SoundInstance.createRandom());
            this.track=track;this.far=far;repeat=true;repeatDelay=0;attenuationType=AttenuationType.NONE;volume=.001f;
        }
        @Override public boolean shouldAlwaysPlay(){return true;}
        @Override public void tick() {
            if(clock-track.lastUpdate>1100||clock-track.lastPower>1100){setDone();return;}
            Frame f=track.sample(listener());
            if(f==null){volume=0;return;}
            x=f.pos.x;y=f.pos.y;z=f.pos.z;
            double d=listener().distanceTo(f.pos),mix=Math.max(0,Math.min(1,(d-80)/400));
            double gain=far?mix/(1+d/900):(1-mix)/(1+d/160);
            volume=(float)(f.power*.9*gain)*Topol.CONFIG.soundVolume;
            pitch=1;
        }
    }
    private static final class Mechanical extends MovingSoundInstance {
        final LauncherEntity entity;final boolean diesel;
        int lived;
        Mechanical(LauncherEntity entity,boolean diesel) {
            super(Topol.sound(diesel?"diesel":"hydraulics"),SoundCategory.BLOCKS,SoundInstance.createRandom());
            this.entity=entity;this.diesel=diesel;repeat=true;repeatDelay=0;attenuationType=AttenuationType.NONE;volume=.001f;
        }
        @Override public boolean shouldAlwaysPlay(){return true;}
        @Override public void tick() {
            lived++;
            int state=entity.state();
            // Allow two ticks for the entity's tracked state to arrive after the effect packet.
            if(entity.isRemoved()||lived>2&&(state!=LauncherEntity.DEPLOYING&&state!=LauncherEntity.STOWING)){setDone();return;}
            x=entity.getX();y=entity.getY()+2;z=entity.getZ();
            double d=listener().distanceTo(entity.getPos());
            volume=(float)((diesel?.22:.42)/(1+d/30))*Topol.CONFIG.soundVolume;
        }
    }
}
