package com.hurma.topol.client;
import com.hurma.topol.Topol;
import com.hurma.topol.net.Packets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import java.util.*;

/** Conventional impact only: brief hot flash, dirt jets, fragments and a low spreading dust cloud. */
public final class Effects {
    private static final Identifier SMOKE=RenderUtil.fx("smoke"),FIRE=RenderUtil.fx("fire"),GLOW=RenderUtil.fx("glow"),
            SPARK=RenderUtil.fx("spark"),DEBRIS=RenderUtil.fx("debris"),SCORCH=RenderUtil.fx("scorch");
    private static final List<Puff> PUFFS=new ArrayList<>();
    private static final List<Lid> LIDS=new ArrayList<>();
    private static final List<Mark> MARKS=new ArrayList<>();
    private static final Set<Long> SEEN=new LinkedHashSet<>();
    private static final Random RNG=new Random(551);
    private static int clock;
    private static class Puff {
        Vec3d p,old,v;int kind,age,life,frame;float size,growth,r,g,b,alpha,angle;
        Puff(int kind,Vec3d p,Vec3d v,float size,float growth,int life,float r,float g,float b,float alpha){
            this.kind=kind;this.p=this.old=p;this.v=v;this.size=size;this.growth=growth;this.life=life;
            this.r=r;this.g=g;this.b=b;this.alpha=alpha;frame=RNG.nextInt(64);angle=RNG.nextFloat()*6.28f;
        }
    }
    private static class Lid {Vec3d p,old,v;int age;float yaw;Lid(Vec3d p,float yaw){this.p=this.old=p;this.yaw=yaw;v=new Vec3d(3,8,1);}}
    private record Mark(Vec3d p,int created,float radius){}
    private Effects(){}
    public static void clear(){PUFFS.clear();LIDS.clear();MARKS.clear();SEEN.clear();clock=0;}
    private static boolean near(Vec3d p){
        return MinecraftClient.getInstance().gameRenderer.getCamera().getPos().squaredDistanceTo(p)<(double)Topol.CONFIG.effectsDistance*Topol.CONFIG.effectsDistance;
    }
    private static void add(Puff p){
        if(near(p.p)&&PUFFS.size()<Topol.CONFIG.particleLimit&&RNG.nextFloat()<Topol.CONFIG.effectDensity)PUFFS.add(p);
    }
    public static void smoke(Vec3d p,Vec3d v,float size,float growth,int life,float r,float g,float b,float alpha){
        add(new Puff(0,p,v,size,growth,life,r,g,b,alpha));
    }
    private static Vec3d random(double scale){return new Vec3d(RNG.nextGaussian()*scale,RNG.nextGaussian()*scale,RNG.nextGaussian()*scale);}
    public static void trail(Vec3d p,Vec3d previous,Vec3d velocity){
        if(!near(p))return;
        double length=p.distanceTo(previous);int samples=Math.min(10,Math.max(1,(int)Math.ceil(length/2.5)));
        for(int i=0;i<samples;i++){
            Vec3d at=previous.lerp(p,i/(double)samples);
            smoke(at,random(.5),.65f,.028f,50+RNG.nextInt(35),.74f,.77f,.76f,.5f);
        }
        add(new Puff(1,p,velocity.normalize().multiply(-5),.8f,.03f,6,1,1,1,.6f));
    }
    public static void effect(Packets.Effect e){
        if(!near(e.pos()))return;
        if(e.kind()==0){
            if(LIDS.size()<Topol.CONFIG.meshDebrisLimit)LIDS.add(new Lid(e.pos(),e.yaw()));
            SoundFx.play("cap",e.pos(),.35f);
        }else if(e.kind()==1){
            SoundFx.play("eject",e.pos(),.7f);SoundFx.play("ignition",e.pos().add(0,3,0),.7f);
            for(int i=0;i<35;i++)smoke(e.pos().add(0,-2.9,0),random(2).add(0,.7,0),1.1f,.05f,70,
                    .65f,.61f,.52f,.65f);
            double d=MinecraftClient.getInstance().gameRenderer.getCamera().getPos().distanceTo(e.pos());
            ViewportFx.shake((float)(.25/(1+d/10)),12);
        }else if(e.kind()==4)SoundFx.play("clunk",e.pos(),.5f);
    }
    public static void impact(Packets.Impact p){
        if(!TopolClient.dimension(p.dimension()))return;
        ClientFlights.remove(p.id());
        if(SEEN.contains(p.id())||p.age()>12)return;
        SEEN.add(p.id());if(SEEN.size()>256)SEEN.remove(SEEN.iterator().next());
        if(!near(p.pos()))return;
        Vec3d at=p.pos();float scale=p.destructive()?1:.3f;
        double d=MinecraftClient.getInstance().gameRenderer.getCamera().getPos().distanceTo(at);
        SoundFx.play(d>100?"blast_far":"blast_near",at,p.destructive()?1:.45f);
        ViewportFx.shake((float)(.9*scale/(1+d/30)),28);
        // Deliberately no full-screen flash, mushroom, thermal glow or nuclear ring.
        for(int i=0;i<12;i++)add(new Puff(1,at.add(random(1.1*scale)),random(3*scale),5*scale,.1f,9+RNG.nextInt(8),1,1,1,.8f));
        add(new Puff(4,at,Vec3d.ZERO,12*scale,0,6,1,.75f,.42f,.7f));
        for(int i=0;i<90;i++){
            double angle=RNG.nextDouble()*Math.PI*2,speed=(3+RNG.nextDouble()*13)*scale;
            Vec3d v=new Vec3d(Math.cos(angle)*speed,RNG.nextDouble()*5*scale,Math.sin(angle)*speed);
            smoke(at.add(0,.4,0),v,1.8f*scale,.055f*scale,85+RNG.nextInt(85),.59f,.54f,.44f,.72f);
        }
        // Narrow dirt jets rise and fall. No sustained buoyant central column.
        for(int i=0;i<36;i++){
            Vec3d v=random(7*scale).add(0,(8+RNG.nextDouble()*13)*scale,0);
            add(new Puff(3,at,v,(.12f+RNG.nextFloat()*.26f)*scale,0,90+RNG.nextInt(40),.7f,.63f,.5f,1));
        }
        for(int i=0;i<20;i++)add(new Puff(2,at,random(9*scale).add(0,4,0),.09f,0,15+RNG.nextInt(20),1,.65f,.23f,1));
        if(MARKS.size()<32)MARKS.add(new Mark(at.add(0,.03,0),clock,2.5f*scale));
    }
    public static void wave(Packets.Wave p){} // block processing is small and doesn't need a nuclear-style visual front
    public static void tick(){
        clock++;var w=MinecraftClient.getInstance().world;
        for(Iterator<Puff> it=PUFFS.iterator();it.hasNext();){
            Puff p=it.next();p.old=p.p;
            if(++p.age>=p.life){it.remove();continue;}
            p.v=p.v.multiply(p.kind==0?.96:.995).add(0,p.kind==2||p.kind==3?-.49:p.kind==0?.006:0,0);
            p.p=p.p.add(p.v.multiply(.05));p.size+=p.growth;
            if(w!=null&&(p.kind==2||p.kind==3)){
                BlockPos b=BlockPos.ofFloored(p.p);
                if(w.isChunkLoaded(b.getX()>>4,b.getZ()>>4)&&!w.getBlockState(b).getCollisionShape(w,b).isEmpty())it.remove();
            }
        }
        for(Iterator<Lid> it=LIDS.iterator();it.hasNext();){
            Lid l=it.next();l.old=l.p;if(++l.age>180){it.remove();continue;}
            l.v=l.v.add(0,-.49,0);l.p=l.p.add(l.v.multiply(.05));
            if(w!=null){
                BlockPos b=BlockPos.ofFloored(l.p);
                if(w.isChunkLoaded(b.getX()>>4,b.getZ()>>4)){
                    double floor=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,b.getX(),b.getZ())+.1;
                    if(l.p.y<floor){l.p=new Vec3d(l.p.x,floor,l.p.z);l.v=new Vec3d(l.v.x*.5,0,l.v.z*.5);}
                }
            }
        }
        MARKS.removeIf(m->clock-m.created>400);
    }
    public static void renderPieces(MatrixStack m,VertexConsumerProvider b,Camera camera,float delta){
        for(Lid l:LIDS){
            Vec3d p=l.old.lerp(l.p,delta);var pr=RenderUtil.project(p,camera.getPos());Vec3d rel=pr.point().subtract(camera.getPos());
            m.push();m.translate(rel.x,rel.y,rel.z);float scale=(float)pr.scale();m.scale(scale,scale,scale);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(l.yaw+l.age*5));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(l.age*8));
            m.translate(0,-3.6,0);
            TMesh.get("launcher",1).emit("cap",m,b,TMesh.texture("launcher",p.distanceTo(camera.getPos())),RenderUtil.light(p),1);
            m.pop();
        }
    }
    public static void render(MatrixStack m,VertexConsumerProvider b,Camera camera,float delta){
        var w=MinecraftClient.getInstance().world;
        float light=w==null?1:.28f+.72f*w.getSkyBrightness(delta);
        ArrayList<Puff> sorted=new ArrayList<>(PUFFS);
        sorted.sort(Comparator.comparingDouble((Puff p)->p.p.squaredDistanceTo(camera.getPos())).reversed());
        for(Puff p:sorted){
            Vec3d at=p.old.lerp(p.p,delta);
            float a=p.alpha*Math.min(1,(p.life-p.age-delta)/(p.kind==0?24f:5f));
            if(p.kind==0)RenderUtil.billboard(m.peek(),b,camera,SMOKE,false,at,p.size,p.frame,true,p.angle,p.r*light,p.g*light,p.b*light,a);
            else if(p.kind==3)RenderUtil.billboard(m.peek(),b,camera,DEBRIS,false,at,p.size,0,false,p.angle,p.r*light,p.g*light,p.b*light,a);
        }
        for(Mark mark:MARKS)RenderUtil.ground(m.peek(),b,camera,SCORCH,mark.p,mark.radius,false,1,1,1,Math.min(1,(400-clock+mark.created)/100f));
        for(Puff p:PUFFS){
            Vec3d at=p.old.lerp(p.p,delta);float a=p.alpha*Math.min(1,(p.life-p.age-delta)/5f);
            if(p.kind==1)RenderUtil.billboard(m.peek(),b,camera,FIRE,true,at,p.size,(p.frame+p.age)&63,true,p.angle,1,1,1,a);
            else if(p.kind==4)RenderUtil.billboard(m.peek(),b,camera,GLOW,true,at,p.size,0,false,0,p.r,p.g,p.b,a);
            else if(p.kind==2)RenderUtil.beam(m.peek(),b,camera,SPARK,at,at.subtract(p.v.multiply(.035)),p.size,p.size*.25f,p.r,p.g,p.b,a);
        }
    }
}
