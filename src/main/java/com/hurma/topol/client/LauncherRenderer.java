package com.hurma.topol.client;
import com.hurma.topol.entity.LauncherEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public final class LauncherRenderer extends EntityRenderer<LauncherEntity>{
    public LauncherRenderer(EntityRendererFactory.Context c){super(c);shadowRadius=0;}
    @Override public Identifier getTexture(LauncherEntity e){return TMesh.texture("launcher",0);}
    @Override public void render(LauncherEntity e,float yaw,float delta,MatrixStack m,VertexConsumerProvider b,int light){
        double d=MinecraftClient.getInstance().gameRenderer.getCamera().getPos().distanceTo(e.getPos());
        TMesh mesh=TMesh.get("launcher",d>100?1:0);Identifier tex=TMesh.texture("launcher",d);
        m.push();m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-e.getYaw()));
        mesh.emit("body",m,b,tex,light,1);
        if(e.loaded())mesh.emit("cap",m,b,tex,light,1);
        mesh.emit("lamps",m,b,tex,light,e.loaded()?1:.18f);
        m.pop();super.render(e,yaw,delta,m,b,light);
    }
}
