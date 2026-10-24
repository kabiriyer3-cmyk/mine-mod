package com.hurma.topol.client;
import com.hurma.topol.Topol;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Original synthesized audio. Game distance delay and muted launch/impact levels. */
public final class SoundFx {
    private record Queued(int due,String name,Vec3d pos,float gain){}
    private static final List<Queued> QUEUE=new ArrayList<>();
    private static int clock;
    private SoundFx(){}
    public static void clear(){QUEUE.clear();clock=0;}
    public static void play(String name,Vec3d p,float gain){
        double d=MinecraftClient.getInstance().gameRenderer.getCamera().getPos().distanceTo(p);
        if(d>Topol.CONFIG.effectsDistance||QUEUE.size()>100)return;
        QUEUE.add(new Queued(clock+(int)(d/343*20),name,p,gain));
    }
    public static void local(String name){
        var mc=MinecraftClient.getInstance();
        mc.getSoundManager().play(new PositionedSoundInstance(Topol.id(name),SoundCategory.MASTER,.4f*Topol.CONFIG.soundVolume,
                1,SoundInstance.createRandom(),false,0,SoundInstance.AttenuationType.NONE,0,0,0,true));
    }
    public static void tick(){
        clock++;var mc=MinecraftClient.getInstance();
        for(Iterator<Queued> it=QUEUE.iterator();it.hasNext();){
            Queued q=it.next();if(q.due>clock)continue;
            double d=mc.gameRenderer.getCamera().getPos().distanceTo(q.pos);
            double reach=q.name.startsWith("blast")?180:q.name.startsWith("roar")?100:55;
            float vol=(float)(q.gain/(1+d/reach))*Topol.CONFIG.soundVolume;
            if(vol>.003)mc.getSoundManager().play(new PositionedSoundInstance(Topol.id(q.name),SoundCategory.BLOCKS,vol,1,
                    SoundInstance.createRandom(),false,0,SoundInstance.AttenuationType.NONE,q.pos.x,q.pos.y,q.pos.z,false));
            it.remove();
        }
    }
}
