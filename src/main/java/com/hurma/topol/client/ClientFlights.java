package com.hurma.topol.client;
import com.hurma.topol.Topol;
import com.hurma.topol.net.Packets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import org.joml.Quaternionf;
import java.util.*;

/** Interpolates server snapshots, not an independent second guidance simulation. */
public final class ClientFlights {
    private static final Map<Long,Flight> FLIGHTS=new LinkedHashMap<>();
    private static class Flight {Vec3d old,p,v;int age,stale;boolean passSound;Flight(Packets.Flight f){old=p=f.pos();v=f.velocity();age=f.age();}}
    private ClientFlights(){}
    public static void clear(){FLIGHTS.clear();}
    public static void remove(long id){FLIGHTS.remove(id);}
    public static void receive(Packets.Flight p){
        if(!TopolClient.dimension(p.dimension()))return;
        Flight f=FLIGHTS.get(p.id());
        if(f==null){if(FLIGHTS.size()<32)FLIGHTS.put(p.id(),new Flight(p));return;}
        if(p.age()==f.age)return;
        f.old=f.p;f.p=p.pos();f.v=p.velocity();f.age=p.age();f.stale=0;
        if(f.age>2)Effects.trail(f.p,f.old,f.v);
        double distance=f.p.distanceTo(MinecraftClient.getInstance().gameRenderer.getCamera().getPos());
        if(!f.passSound&&f.age>8&&distance<100){f.passSound=true;SoundFx.play("sonic_boom",f.p,.5f);}
        if(f.age==4||f.age==24||f.age==44)SoundFx.play(distance<100?"roar_near":"roar_far",f.p,.45f);
    }
    public static void tick(){FLIGHTS.values().removeIf(f->++f.stale>100);}
    public static void renderMeshes(MatrixStack m,VertexConsumerProvider b,Camera camera,float delta){
        for(Flight f:FLIGHTS.values()){
            Vec3d p=f.old.lerp(f.p,delta),axis=f.v.normalize();
            double d=p.distanceTo(camera.getPos());if(d>Topol.CONFIG.effectsDistance)continue;
            var pr=RenderUtil.project(p,camera.getPos());Vec3d rel=pr.point().subtract(camera.getPos());
            m.push();m.translate(rel.x,rel.y,rel.z);float scale=(float)pr.scale();m.scale(scale,scale,scale);
            m.multiply(new Quaternionf().rotationTo(0,1,0,(float)axis.x,(float)axis.y,(float)axis.z));
            TMesh mesh=TMesh.get("missile",d>120?1:0);
            mesh.emit("body",m,b,TMesh.texture("missile",d),RenderUtil.light(p),1);
            if(f.age>2)mesh.emit("fins",m,b,TMesh.texture("missile",d),RenderUtil.light(p),1);
            m.pop();
        }
    }
    public static void renderHot(MatrixStack m,VertexConsumerProvider b,Camera camera,float delta){
        for(Flight f:FLIGHTS.values()){
            if(f.age<2)continue;
            Vec3d p=f.old.lerp(f.p,delta),axis=f.v.normalize();if(p.distanceTo(camera.getPos())>Topol.CONFIG.effectsDistance)continue;
            RenderUtil.beam(m.peek(),b,camera,RenderUtil.fx("plume"),p,p.subtract(axis.multiply(5.5)),.35f,1.7f,1,1,1,.9f);
            RenderUtil.billboard(m.peek(),b,camera,RenderUtil.fx("glow"),true,p,1.8f,0,false,0,1,.75f,.35f,.6f);
        }
    }
}
