package com.hurma.topol.client;
import com.hurma.topol.Topol;
import com.hurma.topol.net.Packets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;

public final class DesignatorHud {
    private static Packets.Aim aim;
    private static int age=100;
    private DesignatorHud(){}
    public static void clear(){aim=null;age=100;}
    public static void receive(Packets.Aim p){aim=p;age=0;}
    public static void tick(){age++;}
    private static boolean using(){
        var mc=MinecraftClient.getInstance();
        return mc.player!=null&&mc.player.isUsingItem()&&mc.player.getActiveItem().isOf(Topol.DESIGNATOR);
    }
    public static void hud(DrawContext c){
        var mc=MinecraftClient.getInstance();
        if(mc.player==null||mc.currentScreen!=null||mc.options.hudHidden)return;
        if(!mc.player.getMainHandStack().isOf(Topol.DESIGNATOR))return;
        int x=c.getScaledWindowWidth()/2,y=c.getScaledWindowHeight()/2;
        boolean active=using()&&age<8&&aim!=null;
        int color=active&&aim.valid()?0xff8fe25e:0xffd4a26b;
        for(int sign:new int[]{-1,1}){
            c.fill(x+sign*18-1,y-7,x+sign*18+1,y+7,color);
            c.fill(x-7,y+sign*18-1,x+7,y+sign*18+1,color);
        }
        String key=!active?"silo.hud.hold":aim.heldTicks()<0?"silo.hud.spent":aim.flying()?"silo.hud.guiding":aim.valid()?"silo.hud.lock":"silo.hud.no_target";
        c.drawCenteredTextWithShadow(mc.textRenderer,Text.translatable(key),x,y+30,color);
        if(active){
            long d=Math.round(mc.player.getEyePos().distanceTo(aim.point()));
            c.drawCenteredTextWithShadow(mc.textRenderer,Text.translatable("silo.hud.range",d),x,y+43,0xa5b99a);
            if(!aim.flying()&&aim.valid()&&aim.heldTicks()>=0){
                c.fill(x-35,y+57,x+35,y+60,0xff273820);
                c.fill(x-35,y+57,x-35+Math.min(70,aim.heldTicks()*7),y+60,color);
            }
        }
    }
    public static void render(MatrixStack m,VertexConsumerProvider b,Camera camera){
        if(!using()||age>8||aim==null||!aim.valid())return;
        var mc=MinecraftClient.getInstance();
        Vec3d p=aim.point();
        float size=(float)Math.max(.12,Math.min(.8,camera.getPos().distanceTo(p)*.0015));
        RenderUtil.billboard(m.peek(),b,camera,RenderUtil.fx("glow"),true,p,size,0,false,0,.5f,1,.25f,.9f);
        Vec3d start=mc.player.getEyePos().add(0,-.18,0);
        RenderUtil.beam(m.peek(),b,camera,RenderUtil.fx("laser"),start,p,.012f,.018f,.35f,1,.17f,.3f);
    }
}
