package com.hurma.topol.client;
import com.hurma.topol.Topol;
import com.hurma.topol.net.Packets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/**
 * Interpolates server snapshots (no second guidance simulation). Orientation follows velocity with a short
 * roll programme so the missile's top ends up facing the sky; fins pop out of the tube, booster drops, wings swing out.
 */
public final class ClientFlights {
    private static final Map<Long,Flight> FLIGHTS=new LinkedHashMap<>();
    private static final Random RNG=new Random(77);
    /** Ticks between booster ignition and the round starting to move (server: LAUNCH_DELAY - IGNITION). */
    private static final int IGNITION_LEAD=6;
    private static final class Flight {
        Vec3d old,p,v,up,oldUp,mouth;int age,delay,stale;float yaw;boolean passSound,dropped,ignited;
        Flight(Packets.Flight f){
            old=p=f.pos();v=f.velocity();age=f.age();delay=f.delay();yaw=f.yaw();
            Vector3f top=new Vector3f(0,0,-1).rotate(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            up=oldUp=new Vec3d(top.x,top.y,top.z);
            if(age==0)mouth=p.add(0,6.9,0);
            dropped=age>Models.BOOSTER_DROP;ignited=delay<=IGNITION_LEAD||age>0;
        }
    }
    private ClientFlights(){}
    public static void clear(){FLIGHTS.clear();}
    public static void remove(long id){FLIGHTS.remove(id);}
    public static void receive(Packets.Flight p){
        if(!TopolClient.dimension(p.dimension()))return;
        Flight f=FLIGHTS.get(p.id());
        if(f==null){if(FLIGHTS.size()<32)FLIGHTS.put(p.id(),new Flight(p));return;}
        f.stale=0;f.delay=p.delay();
        if(!f.ignited&&f.delay<=IGNITION_LEAD)f.ignited=true;
        if(p.age()==f.age)return;
        f.old=f.p;f.p=p.pos();f.v=p.velocity();f.age=p.age();
        f.oldUp=f.up;f.up=rollTowards(f);
        Effects.trail(f.p,f.old,f.v,f.age<Models.BOOSTER_DROP);
        if(!f.dropped&&f.age>=Models.BOOSTER_DROP){
            f.dropped=true;
            Effects.booster(f.p,f.v.multiply(.55),Models.orient(f.v.normalize(),f.up));
        }
        double distance=f.p.distanceTo(MinecraftClient.getInstance().gameRenderer.getCamera().getPos());
        if(!f.passSound&&f.age>8&&distance<90){f.passSound=true;SoundFx.play("sonic_boom",f.p,.55f);}
        if(f.age==3||f.age==22||f.age==42)SoundFx.play(distance<100?"roar_near":"roar_far",f.p,.5f);
    }
    /** Desired top = world up projected off the axis; while still vertical keep the silo's orientation. */
    private static Vec3d rollTowards(Flight f){
        Vec3d axis=f.v.normalize(),up=new Vec3d(0,1,0);
        Vec3d want=up.subtract(axis.multiply(axis.dotProduct(up)));
        if(want.lengthSquared()<.0025)return f.up;
        want=want.normalize();
        return f.up.lerp(want,.35).normalize();
    }
    public static void tick(){
        for(Iterator<Flight> it=FLIGHTS.values().iterator();it.hasNext();){
            Flight f=it.next();
            if(++f.stale>100){it.remove();continue;}
            // exhaust boiling out of the mouth while the booster burns inside the canister / just above it
            if(f.mouth!=null&&f.ignited&&f.age<7)Effects.siloExhaust(f.mouth,f.age);
        }
    }
    private static float age(Flight f,float delta){return f.delay>0?0:f.age+delta;}
    public static void renderMeshes(MatrixStack m,VertexConsumerProvider b,Camera camera,float delta){
        for(Flight f:FLIGHTS.values()){
            Vec3d p=f.old.lerp(f.p,delta);
            double d=p.distanceTo(camera.getPos());if(d>Topol.CONFIG.effectsDistance)continue;
            Vec3d axis=f.age<1?new Vec3d(0,1,0):f.v.normalize();
            Vec3d up=f.oldUp.lerp(f.up,delta);
            var pr=RenderUtil.project(p,camera.getPos());Vec3d rel=pr.point().subtract(camera.getPos());
            m.push();m.translate(rel.x,rel.y,rel.z);float scale=(float)pr.scale();m.scale(scale,scale,scale);
            m.multiply(Models.orient(axis,up));
            float a=age(f,delta);
            Models.missile(m,b,TMesh.lod(d),RenderUtil.light(p.add(axis.multiply(3))),Models.fins(a),Models.wings(a),Models.intake(a),f.age<Models.BOOSTER_DROP);
            m.pop();
        }
    }
    /** Additive booster plume and nozzle glow. Cruise phase: small hot core only. */
    public static void renderHot(MatrixStack m,VertexConsumerProvider b,Camera camera,float delta){
        for(Flight f:FLIGHTS.values()){
            if(!f.ignited)continue;
            Vec3d p=f.old.lerp(f.p,delta),axis=f.age<1?new Vec3d(0,1,0):f.v.normalize();
            if(p.distanceTo(camera.getPos())>Topol.CONFIG.effectsDistance)continue;
            float flicker=.85f+.15f*RNG.nextFloat();
            if(f.age<Models.BOOSTER_DROP){
                float len=(f.age<2?2.2f:3.2f)*flicker;
                RenderUtil.beam(m.peek(),b,camera,RenderUtil.fx("plume"),p,p.subtract(axis.multiply(len)),.5f,.95f,1,1,1,.95f);
                RenderUtil.beam(m.peek(),b,camera,RenderUtil.fx("plume"),p,p.subtract(axis.multiply(len*.55)),.32f,.5f,1,.95f,.85f,.9f);
                RenderUtil.billboard(m.peek(),b,camera,RenderUtil.fx("glow"),true,p.subtract(axis.multiply(.2)),2.6f*flicker,0,false,0,1,.72f,.38f,.75f);
            }else if(f.age<Models.BOOSTER_DROP+60){
                RenderUtil.billboard(m.peek(),b,camera,RenderUtil.fx("glow"),true,p.add(axis.multiply(.3)),.55f*flicker,0,false,0,1,.6f,.3f,.45f);
            }
        }
    }
}
