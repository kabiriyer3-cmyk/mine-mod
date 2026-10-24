package com.hurma.topol.client;

import com.hurma.topol.Topol;
import com.hurma.topol.net.Packets;
import com.hurma.topol.sim.FlightPath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.joml.Quaternionf;
import java.util.*;

/** Non-entity flight rendering, so visual range does not depend on entity chunk tracking. */
public final class ClientFlights {
    private static final LinkedHashMap<Long,Flight> FLIGHTS=new LinkedHashMap<>();
    private static final Identifier PLUME=RenderUtil.fx("plume"),GLOW=RenderUtil.fx("glow"),FLARE=RenderUtil.fx("flare");
    private ClientFlights(){}
    private record Pose(Vec3d point,Vec3d velocity,Vec3d axis,Quaternionf rotation,FlightPath.Sample sample,double time) {}
    private static final class Flight {
        final long id;final Vec3d launch,target,dir,side;final FlightPath path;final int duration;
        float age;double previousTime;boolean sonic;
        Flight(Packets.Flight p) {
            id=p.id();launch=p.launch();target=p.target();duration=p.duration();age=p.age();previousTime=age*.05;
            Vec3d horizontal=new Vec3d(target.x-launch.x,0,target.z-launch.z);
            dir=horizontal.normalize();side=new Vec3d(-dir.z,0,dir.x);
            path=new FlightPath(horizontal.length(),target.y-launch.y,duration);
        }
        Pose pose(double ticks) {
            double t=Math.max(0,Math.min(duration,ticks))*.05;
            FlightPath.Sample s=path.at(t);
            Vec3d p=launch.add(dir.multiply(s.x())).add(0,s.y(),0).add(side.multiply(s.side()));
            Vec3d v=dir.multiply(s.vx()).add(0,s.vy(),0).add(side.multiply(s.vs()));
            Vec3d axis=v.lengthSquared()>.0001?v.normalize():new Vec3d(0,1,0);
            Quaternionf q=new Quaternionf().rotationTo(0,1,0,(float)axis.x,(float)axis.y,(float)axis.z);
            return new Pose(p,v,axis,q,s,t);
        }
    }
    public static void clear(){FLIGHTS.clear();}
    public static void remove(long id){FLIGHTS.remove(id);}
    public static void receive(Packets.Flight p) {
        if(!TopolClient.dimension(p.dimension())||p.duration()<1600||p.duration()>5000)return;
        Flight f=FLIGHTS.get(p.id());
        if(f==null){if(FLIGHTS.size()<32)FLIGHTS.put(p.id(),new Flight(p));}
        else if(Math.abs(f.age-p.age())>3) {
            f.age=p.age();f.previousTime=f.age*.05; // don't replay old separation/audio events
        }
    }
    public static void tick() {
        for(Iterator<Flight> it=FLIGHTS.values().iterator();it.hasNext();) {
            Flight f=it.next();f.age++;
            if(f.age>f.duration+100){it.remove();continue;}
            Pose p=f.pose(f.age);
            double t=p.time;
            if(cross(f.previousTime,t,FlightPath.EXIT_TIME)) {
                Effects.piece("missile",new String[]{"tray"},p.point.add(p.axis.multiply(-.85)),p.velocity.add(f.side.multiply(2)),
                        new Vec3d(0,-.85,0),p.rotation,1.3f);
            }
            if(cross(f.previousTime,t,FlightPath.IGNITION_TIME)||cross(f.previousTime,t,FlightPath.STAGE_2_START)||
                    cross(f.previousTime,t,FlightPath.STAGE_3_START))Effects.ignition(nozzle(p));
            if(cross(f.previousTime,t,FlightPath.STAGE_1_END))
                drop(p,f,new String[]{"s1","inter12","fin0","fin1","fin2","fin3"},4.2);
            if(cross(f.previousTime,t,FlightPath.STAGE_2_END))drop(p,f,new String[]{"s2","s2_nozzle","inter23"},11.6);
            if(cross(f.previousTime,t,FlightPath.STAGE_3_END)) {
                drop(p,f,new String[]{"s3","s3_nozzle"},15.7);
                Effects.piece("missile",new String[]{"nose"},p.point.add(p.axis.multiply(19)),p.velocity.add(f.side.multiply(4)),
                        new Vec3d(0,19,0),p.rotation,.5f);
            }
            if(p.sample.thrust())Effects.trail(nozzle(p),p.axis,p.velocity,p.sample.stage(),t);
            if(p.sample.phase()==FlightPath.Phase.REENTRY) {
                Effects.reentry(p.point,p.axis,p.velocity);
                if(!f.sonic&&p.point.distanceTo(MinecraftClient.getInstance().gameRenderer.getCamera().getPos())<700) {
                    f.sonic=true;SoundFx.play("sonic_boom",p.point,.8f,true);
                }
            }
            SoundFx.flight(f.id,nozzle(p),p.sample.thrust());
            f.previousTime=t;
        }
    }
    private static boolean cross(double previous,double now,double event){return previous<event&&now>=event;}
    private static void drop(Pose p,Flight f,String[] names,double center) {
        Effects.piece("missile",names,p.point.add(p.axis.multiply(center)),p.velocity.subtract(p.axis.multiply(2)).add(f.side.multiply(1.5)),
                new Vec3d(0,center,0),p.rotation,.25f);
        SoundFx.play("stage_sep",p.point,.7f,true);Effects.sparks(p.point.add(p.axis.multiply(center)),16,3);
    }
    private static double nozzleOffset(int stage){return stage==0?-.75:stage==1?8.35:stage==2?13.45:0;}
    private static Vec3d nozzle(Pose p){return p.point.add(p.axis.multiply(nozzleOffset(p.sample.stage())));}
    private static double rvOffset(double time){return 17.3-19.6*FlightPath.smooth((time-FlightPath.STAGE_3_END)/15);}
    public static void renderMeshes(MatrixStack m,VertexConsumerProvider consumers,Camera camera,float delta) {
        for(Flight f:FLIGHTS.values()) {
            Pose p=f.pose(f.age-1+delta);
            double distance=p.point.distanceTo(camera.getPos());
            if(distance>Topol.CONFIG.effectsDistance)continue;
            int stage=p.sample.stage();
            Vec3d pos=stage>=3?p.point.add(p.axis.multiply(rvOffset(p.time))):p.point;
            RenderUtil.Projected pr=RenderUtil.project(pos,camera.getPos());
            Vec3d rel=pr.point().subtract(camera.getPos());
            m.push();m.translate(rel.x,rel.y,rel.z);m.scale((float)pr.scale(),(float)pr.scale(),(float)pr.scale());m.multiply(p.rotation);
            TMesh mesh=TMesh.get(stage>=3?"rv":"missile",distance>160?1:0);
            Identifier tex=TMesh.texture("missile",distance);int light=RenderUtil.light(p.point);
            if(stage>=3)mesh.emit("rv",m,consumers,tex,light,1);
            else {
                if(stage==0) {
                    mesh.emit("s1",m,consumers,tex,light,1);mesh.emit("inter12",m,consumers,tex,light,1);
                    if(p.time<FlightPath.EXIT_TIME)mesh.emit("tray",m,consumers,tex,light,1);
                    if(p.time>=FlightPath.EXIT_TIME)for(int i=0;i<4;i++) {
                        float a=(float)(i*Math.PI/2+Math.PI/4);
                        float fold=(float)((1-FlightPath.smooth((p.time-FlightPath.EXIT_TIME)/.9))*Math.PI/2);
                        m.push();m.multiply(RotationAxis.POSITIVE_Y.rotation(-a));
                        m.translate(.96,.15,0);m.multiply(RotationAxis.POSITIVE_Z.rotation(fold));m.translate(-.96,-.15,0);
                        m.multiply(RotationAxis.POSITIVE_Y.rotation(a));
                        mesh.emit("fin"+i,m,consumers,tex,light,1);m.pop();
                    }
                }
                if(stage<=1) {
                    mesh.emit("s2",m,consumers,tex,light,1);mesh.emit("inter23",m,consumers,tex,light,1);
                    if(stage==1)mesh.emit("s2_nozzle",m,consumers,tex,light,1);
                }
                mesh.emit("s3",m,consumers,tex,light,1);mesh.emit("nose",m,consumers,tex,light,1);
                if(stage==2)mesh.emit("s3_nozzle",m,consumers,tex,light,1);
            }
            m.pop();
        }
    }
    public static void renderHot(MatrixStack m,VertexConsumerProvider consumers,Camera camera,float delta) {
        for(Flight f:FLIGHTS.values()) {
            Pose p=f.pose(f.age-1+delta);
            if(p.point.distanceTo(camera.getPos())>Topol.CONFIG.effectsDistance)continue;
            if(p.sample.thrust()) {
                Vec3d nozzle=nozzle(p);
                float shimmer=(float)(.95+.05*Math.sin(p.time*91));
                float length=(p.sample.stage()==0?28:p.sample.stage()==1?21:16)*shimmer;
                RenderUtil.beam(m.peek(),consumers,camera,PLUME,nozzle,nozzle.subtract(p.axis.multiply(length)),1.4f,9,1,1,1,1);
                RenderUtil.billboard(m.peek(),consumers,camera,GLOW,true,nozzle,7,0,false,0,1,.8f,.4f,.85f);
                if(p.point.distanceTo(camera.getPos())>500)
                    RenderUtil.billboard(m.peek(),consumers,camera,FLARE,true,nozzle,10,0,false,0,1,.7f,.4f,.45f);
            } else if(p.sample.phase()==FlightPath.Phase.REENTRY) {
                Vec3d tip=p.point;
                RenderUtil.billboard(m.peek(),consumers,camera,GLOW,true,tip,5,0,false,0,.6f,.8f,1,.85f);
                RenderUtil.beam(m.peek(),consumers,camera,PLUME,tip,tip.subtract(p.axis.multiply(23)),2,6,1,.48f,.2f,.8f);
            }
        }
    }
    public static void hud(DrawContext c) {
        var mc=MinecraftClient.getInstance();
        if(mc.player==null||mc.currentScreen!=null||mc.options.hudHidden)return;
        for(Flight f:FLIGHTS.values()) {
            Vec3d player=mc.player.getPos();
            if(player.distanceTo(f.launch)>200&&player.distanceTo(f.target)>300)continue;
            Pose p=f.pose(f.age);
            int width=228,x=c.getScaledWindowWidth()-width-8,y=10;
            c.fill(x,y,x+width,y+36,0x99141d15);
            Text phase=Text.translatable("phase.topol."+p.sample.phase().name().toLowerCase(Locale.ROOT));
            c.drawText(mc.textRenderer,Text.translatable("hud.topol.flight",phase),x+7,y+5,0xc2d6a6,false);
            c.drawText(mc.textRenderer,Text.translatable("hud.topol.data",Math.round(Math.max(0,p.point.y-f.target.y)),
                    Math.round(p.velocity.length()),Math.max(0,(f.duration-(int)f.age)/20)),x+7,y+19,0x9fac91,false);
            break;
        }
    }
}
