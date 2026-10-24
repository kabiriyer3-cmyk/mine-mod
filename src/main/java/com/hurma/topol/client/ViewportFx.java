package com.hurma.topol.client;

import com.hurma.topol.Topol;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import java.util.*;

/** Camera-only offsets: never changes player yaw, pitch, movement, or server aim. */
public final class ViewportFx {
    private record Impulse(int start,int duration,float strength){}
    private static final List<Impulse> IMPULSES=new ArrayList<>();
    private static int clock;
    private static float flash;
    private ViewportFx(){}
    public static void clear(){IMPULSES.clear();clock=0;flash=0;}
    public static void shake(float strength,int duration){
        if(Topol.CONFIG.enableCameraShake&&IMPULSES.size()<20)IMPULSES.add(new Impulse(clock,duration,strength));
    }
    public static void flash(float strength){
        if(Topol.CONFIG.enableFlash)flash=Math.max(flash,strength*Topol.CONFIG.flashStrength);
    }
    public static void tick(){clock++;flash*=.80f;IMPULSES.removeIf(i->clock-i.start>i.duration);}
    private static float amplitude(float delta) {
        if(!Topol.CONFIG.enableCameraShake)return 0;
        float a=0;
        for(Impulse i:IMPULSES) {
            double u=(clock+delta-i.start)/i.duration;
            if(u>=0&&u<1)a+=i.strength*(float)((1-u)*(1-u));
        }
        return Math.min(2,a)*Topol.CONFIG.cameraShake;
    }
    public static float yaw(float delta){double t=(clock+delta)*.05;return amplitude(delta)*(float)(Math.sin(t*73)*.55+Math.sin(t*113)*.22);}
    public static float pitch(float delta){double t=(clock+delta)*.05;return amplitude(delta)*(float)(Math.sin(t*83+.9)*.6+Math.sin(t*127)*.18);}
    public static void render(DrawContext c) {
        if(MinecraftClient.getInstance().world==null||flash<.005)return;
        int a=Math.min(200,Math.max(0,(int)(flash*255)));
        c.fill(0,0,c.getScaledWindowWidth(),c.getScaledWindowHeight(),(a<<24)|0xfff5dd);
    }
}
