package com.hurma.topol.client;
import com.hurma.topol.entity.LauncherEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Embedded silo: concrete headworks, VLS cell canister in the shaft, animated hatch, stowed round when loaded. */
public final class LauncherRenderer extends EntityRenderer<LauncherEntity>{
    public LauncherRenderer(EntityRendererFactory.Context c){super(c);shadowRadius=0;}
    @Override public Identifier getTexture(LauncherEntity e){return TMesh.texture("steel",0);}
    @Override public void render(LauncherEntity e,float yaw,float delta,MatrixStack m,VertexConsumerProvider b,int light){
        double d=MinecraftClient.getInstance().gameRenderer.getCamera().getPos().distanceTo(e.getPos());
        int lod=TMesh.lod(d);
        m.push();m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-e.getYaw()));
        Models.silo(m,b,lod,light,LauncherEntity.hatch(e.sinceLaunch()+delta,e.loaded()));
        if(e.loaded()&&d<160){
            m.push();m.translate(0,Models.STOWED_TAIL,0);
            Models.missile(m,b,lod,light,0,0,0,true);
            m.pop();
        }
        m.pop();super.render(e,yaw,delta,m,b,light);
    }
}
