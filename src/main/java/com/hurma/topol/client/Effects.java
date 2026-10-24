package com.hurma.topol.client;
import com.hurma.topol.Topol;
import com.hurma.topol.net.Packets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import org.joml.Quaternionf;
import java.util.*;

/**
 * Custom sprite FX, no vanilla particles. Velocities are blocks/second, positions advance v/20 per tick.
 * Smoke: lit cauliflower puffs (alpha). Fire, glow, sparks: additive. Dirt chunks: alpha sprites with gravity.
 */
public final class Effects {
    private static final Identifier SMOKE=RenderUtil.fx("smoke"),FIRE=RenderUtil.fx("fire"),GLOW=RenderUtil.fx("glow"),
            SPARK=RenderUtil.fx("spark"),DEBRIS=RenderUtil.fx("debris"),SCORCH=RenderUtil.fx("scorch");
    private static final int SMOKE_K=0,FIRE_K=1,SPARK_K=2,CHUNK_K=3,GLOW_K=4;
    private static final List<Puff> PUFFS=new ArrayList<>();
    private static final List<Piece> PIECES=new ArrayList<>();
    private static final List<Mark> MARKS=new ArrayList<>();
    private static final Set<Long> SEEN=new LinkedHashSet<>();
    private static final Random RNG=new Random(551);
    private static int clock;
    private static final class Puff {
        Vec3d p,old,v;int kind,age,life,frame;float size,growth,r,g,b,alpha,angle,drag=.955f,lift=.03f;
        Puff(int kind,Vec3d p,Vec3d v,float size,float growth,int life,float r,float g,float b,float alpha){
            this.kind=kind;this.p=this.old=p;this.v=v;this.size=size;this.growth=growth;this.life=life;
            this.r=r;this.g=g;this.b=b;this.alpha=alpha;frame=RNG.nextInt(64);
            // Smoke sprites are lit from the top: keep them nearly upright. Fire may spin freely.
            angle=kind==SMOKE_K?(float)RNG.nextGaussian()*.15f:RNG.nextFloat()*6.28f;
        }
        Puff drag(float d){drag=d;return this;}
        Puff lift(float l){lift=l;return this;}
    }
    /** Spent booster: real mesh, tumbling, trailing a little smoke. */
    private static final class Piece {
        Vec3d p,old,v;Quaternionf q;float spinX,spinZ;int age;
        Piece(Vec3d p,Vec3d v,Quaternionf q){this.p=this.old=p;this.v=v;this.q=q;spinX=(float)RNG.nextGaussian()*9;spinZ=(float)RNG.nextGaussian()*6;}
    }
    private record Mark(Vec3d p,int created,float radius){}
    private Effects(){}
    public static void clear(){PUFFS.clear();PIECES.clear();MARKS.clear();SEEN.clear();clock=0;}
    private static Vec3d camera(){return MinecraftClient.getInstance().gameRenderer.getCamera().getPos();}
    private static boolean near(Vec3d p){return camera().squaredDistanceTo(p)<(double)Topol.CONFIG.effectsDistance*Topol.CONFIG.effectsDistance;}
    private static Puff add(Puff p){
        if(near(p.p)&&PUFFS.size()<Topol.CONFIG.particleLimit&&RNG.nextFloat()<Topol.CONFIG.effectDensity)PUFFS.add(p);
        return p;
    }
    private static Puff smoke(Vec3d p,Vec3d v,float size,float growth,int life,float r,float g,float b,float alpha){
        return add(new Puff(SMOKE_K,p,v,size,growth,life,r,g,b,alpha));
    }
    private static Vec3d random(double scale){return new Vec3d(RNG.nextGaussian()*scale,RNG.nextGaussian()*scale,RNG.nextGaussian()*scale);}
    private static float range(float a,float b){return a+RNG.nextFloat()*(b-a);}

    /** Booster phase: dense white trail. Cruise: thin, short-lived haze. */
    public static void trail(Vec3d p,Vec3d previous,Vec3d velocity,boolean booster){
        if(!near(p))return;
        double length=p.distanceTo(previous);
        int samples=booster?Math.min(16,Math.max(1,(int)Math.ceil(length/1.4))):Math.min(8,Math.max(1,(int)Math.ceil(length/3.2)));
        Vec3d back=velocity.normalize().multiply(-1);
        for(int i=0;i<samples;i++){
            Vec3d at=previous.lerp(p,i/(double)samples);
            if(booster){
                float l=range(.9f,1f);
                smoke(at.add(random(.12)),random(.6).add(back.multiply(2)),range(.8f,1.1f),.075f,70+RNG.nextInt(45),l,l,l*.985f,.82f);
            }else smoke(at.add(random(.1)),random(.25),.42f,.035f,35+RNG.nextInt(25),.95f,.95f,.95f,.32f);
        }
        if(booster)for(int i=0;i<2;i++)add(new Puff(FIRE_K,p.add(back.multiply(.6+i*.7)),back.multiply(6),range(.9f,1.3f),.06f,4,1,.9f,.75f,.75f));
    }
    /** Launch: white exhaust boiling out of the mouth, warm near the plume, tan dust ring rolling along the ground. */
    public static void siloExhaust(Vec3d mouth,int age){
        if(!near(mouth))return;
        for(int i=0;i<7;i++){
            double a=RNG.nextDouble()*Math.PI*2,out=range(1.5f,6f);
            Vec3d v=new Vec3d(Math.cos(a)*out,range(2f,8f),Math.sin(a)*out);
            float warm=age<2?.9f:1;
            smoke(mouth.add(random(.25)),v,range(1.0f,1.5f),.075f,90+RNG.nextInt(60),.98f,.95f*warm,.9f*warm,.85f);
        }
        if(age<4)for(int i=0;i<5;i++){
            double a=RNG.nextDouble()*Math.PI*2,out=range(6f,11f);
            Vec3d v=new Vec3d(Math.cos(a)*out,range(.2f,1.2f),Math.sin(a)*out);
            float l=range(.58f,.66f);
            smoke(mouth.add(Math.cos(a)*.9,-.15,Math.sin(a)*.9),v,range(.9f,1.3f),.09f,100+RNG.nextInt(60),l,l*.9f,l*.74f,.75f).drag(.93f).lift(.005f);
        }
        for(int i=0;i<2;i++)add(new Puff(FIRE_K,mouth.add(random(.2)),new Vec3d(0,range(4,9),0),range(1.2f,1.8f),.08f,5,1,.88f,.7f,.8f));
        add(new Puff(GLOW_K,mouth.add(0,.4,0),Vec3d.ZERO,5.5f,0,2,1,.62f,.3f,.5f));
    }
    public static void booster(Vec3d p,Vec3d v,Quaternionf q){
        if(near(p)&&PIECES.size()<Topol.CONFIG.meshDebrisLimit)PIECES.add(new Piece(p,v,q));
        for(int i=0;i<6;i++)smoke(p.add(random(.3)),random(1.5),1.0f,.08f,60,.9f,.9f,.88f,.7f);
    }
    public static void effect(Packets.Effect e){
        if(!near(e.pos()))return;
        double d=camera().distanceTo(e.pos());
        switch(e.kind()){
            case 0 -> {SoundFx.play("clunk",e.pos(),.6f);SoundFx.play("cap",e.pos(),.7f);}
            case 1 -> {
                SoundFx.play("ignition",e.pos(),.9f);SoundFx.play("eject",e.pos(),.6f);
                ViewportFx.shake((float)(.35/(1+d/10)),22);
            }
            case 4 -> SoundFx.play("clunk",e.pos(),.5f);
            case 6 -> {SoundFx.play("cap",e.pos(),.45f);SoundFx.play("clunk",e.pos(),.45f);}
            default -> {}
        }
    }
    public static void impact(Packets.Impact p){
        if(!TopolClient.dimension(p.dimension()))return;
        ClientFlights.remove(p.id());
        if(SEEN.contains(p.id())||p.age()>12)return;
        SEEN.add(p.id());if(SEEN.size()>256)SEEN.remove(SEEN.iterator().next());
        if(!near(p.pos()))return;
        Vec3d at=p.pos();float s=p.destructive()?1:.35f;
        double d=camera().distanceTo(at);
        SoundFx.play(d>100?"blast_far":"blast_near",at,p.destructive()?1:.45f);
        ViewportFx.shake((float)(.9*s/(1+d/30)),30);
        // fireball core: short additive puffs expanding from the burst point
        for(int i=0;i<40;i++){
            Vec3d dir=random(1).normalize();double r=RNG.nextDouble()*1.4*s;
            Vec3d v=new Vec3d(dir.x,Math.abs(dir.y)*.9+.25,dir.z).multiply((4+RNG.nextDouble()*9)*s);
            add(new Puff(FIRE_K,at.add(dir.multiply(r)).add(0,.8*s,0),v,range(1.6f,2.8f)*s,.16f*s,8+RNG.nextInt(10),1,.86f,.7f,.9f).drag(.86f));
        }
        add(new Puff(GLOW_K,at.add(0,1.5*s,0),Vec3d.ZERO,16*s,0,9,1,.55f,.22f,.75f));
        add(new Puff(GLOW_K,at.add(0,1*s,0),Vec3d.ZERO,7*s,0,4,1,.95f,.8f,.9f));
        // dark smoke and dirt shell, billowing up and lingering
        for(int i=0;i<46;i++){
            Vec3d dir=random(1).normalize();dir=new Vec3d(dir.x,Math.abs(dir.y)*.8+.15,dir.z).normalize();
            float l=range(.2f,.34f);
            smoke(at.add(dir.multiply(1.2*s)).add(0,.6*s,0),dir.multiply((5+RNG.nextDouble()*8)*s).add(0,2,0),range(2.2f,3.2f)*s,.06f*s,
                    150+RNG.nextInt(110),l*1.1f,l,l*.9f,.95f).drag(.93f).lift(.045f);
        }
        // brown dust skirt rolling outwards along the ground
        for(int i=0;i<30;i++){
            double a=RNG.nextDouble()*Math.PI*2,out=(10+RNG.nextDouble()*12)*s;
            float l=range(.5f,.6f);
            smoke(at.add(Math.cos(a)*2*s,.5,Math.sin(a)*2*s),new Vec3d(Math.cos(a)*out,RNG.nextDouble()*1.5,Math.sin(a)*out),range(1.8f,2.8f)*s,.07f*s,
                    120+RNG.nextInt(90),l,l*.84f,l*.65f,.8f).drag(.92f).lift(.01f);
        }
        // flying dirt chunks
        for(int i=0;i<36;i++){
            Vec3d v=random(5*s).add(0,(8+RNG.nextDouble()*12)*s,0);
            add(new Puff(CHUNK_K,at.add(0,.5,0),v,range(.16f,.42f)*s,0,80+RNG.nextInt(40),.2f,.16f,.12f,1).drag(.995f));
        }
        for(int i=0;i<24;i++)add(new Puff(SPARK_K,at.add(0,.6,0),random(10*s).add(0,5*s,0),.09f,0,12+RNG.nextInt(16),1,.66f,.25f,1).drag(.97f));
        // Real blocks become the crater; a decal only for the non-destructive case.
        if(!p.destructive()&&MARKS.size()<32)MARKS.add(new Mark(at.add(0,.03,0),clock,1.6f));
    }
    public static void wave(Packets.Wave p){}
    public static void tick(){
        clock++;var w=MinecraftClient.getInstance().world;
        for(Iterator<Puff> it=PUFFS.iterator();it.hasNext();){
            Puff p=it.next();p.old=p.p;
            if(++p.age>=p.life){it.remove();continue;}
            boolean ballistic=p.kind==SPARK_K||p.kind==CHUNK_K;
            p.v=p.v.multiply(p.drag).add(0,ballistic?-.49:p.kind==SMOKE_K?p.lift*20*.05:0,0);
            p.p=p.p.add(p.v.multiply(.05));
            p.size+=p.growth*Math.max(.25f,1-p.age/(float)p.life);
            if(w!=null&&ballistic){
                BlockPos b=BlockPos.ofFloored(p.p);
                if(w.isChunkLoaded(b.getX()>>4,b.getZ()>>4)&&!w.getBlockState(b).getCollisionShape(w,b).isEmpty())it.remove();
            }
        }
        for(Iterator<Piece> it=PIECES.iterator();it.hasNext();){
            Piece l=it.next();l.old=l.p;if(++l.age>220){it.remove();continue;}
            l.v=l.v.multiply(.985).add(0,-.49,0);l.p=l.p.add(l.v.multiply(.05));
            if(l.age<30&&l.age%2==0)smoke(l.p,random(.4),.55f,.05f,45,.85f,.85f,.84f,.55f);
            if(w!=null){
                BlockPos b=BlockPos.ofFloored(l.p);
                if(w.isChunkLoaded(b.getX()>>4,b.getZ()>>4)){
                    double floor=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,b.getX(),b.getZ())+.18;
                    if(l.p.y<floor){
                        if(l.v.y<-6)SoundFx.play("earth",l.p,.5f);
                        l.p=new Vec3d(l.p.x,floor,l.p.z);l.v=new Vec3d(l.v.x*.4,-l.v.y*.2,l.v.z*.4);l.spinX*=.5f;l.spinZ*=.5f;
                    }
                }
            }
        }
        MARKS.removeIf(m->clock-m.created>400);
    }
    public static void renderPieces(MatrixStack m,VertexConsumerProvider b,Camera camera,float delta){
        for(Piece l:PIECES){
            Vec3d p=l.old.lerp(l.p,delta);var pr=RenderUtil.project(p,camera.getPos());Vec3d rel=pr.point().subtract(camera.getPos());
            double d=p.distanceTo(camera.getPos());if(d>400)continue;
            m.push();m.translate(rel.x,rel.y,rel.z);float scale=(float)pr.scale();m.scale(scale,scale,scale);
            m.multiply(l.q);
            float t=l.age+delta;
            m.translate(0,.19f,0);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(t*l.spinX));m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(t*l.spinZ));
            m.translate(0,-.19f,0);
            Models.booster(m,b,TMesh.lod(d),RenderUtil.light(p));
            m.pop();
        }
    }
    public static void render(MatrixStack m,VertexConsumerProvider b,Camera camera,float delta){
        var w=MinecraftClient.getInstance().world;
        float light=w==null?1:.25f+.75f*w.getSkyBrightness(delta);
        ArrayList<Puff> sorted=new ArrayList<>();
        for(Puff p:PUFFS)if(p.kind==SMOKE_K||p.kind==CHUNK_K)sorted.add(p);
        sorted.sort(Comparator.comparingDouble((Puff p)->p.p.squaredDistanceTo(camera.getPos())).reversed());
        for(Puff p:sorted){
            Vec3d at=p.old.lerp(p.p,delta);
            float t=p.age+delta,fadeIn=Math.min(1,t/3f),fadeOut=Math.min(1,(p.life-t)/(p.life*.45f));
            float a=p.alpha*Math.max(0,Math.min(fadeIn,fadeOut));
            if(p.kind==SMOKE_K)RenderUtil.billboard(m.peek(),b,camera,SMOKE,false,at,p.size,p.frame,true,p.angle,p.r*light,p.g*light,p.b*light,a);
            else RenderUtil.billboard(m.peek(),b,camera,DEBRIS,false,at,p.size,0,false,p.angle+t*.3f,p.r*light,p.g*light,p.b*light,Math.min(1,(p.life-t)/6f));
        }
        for(Mark mark:MARKS)RenderUtil.ground(m.peek(),b,camera,SCORCH,mark.p,mark.radius,false,1,1,1,Math.min(1,(400-clock+mark.created)/100f));
        for(Puff p:PUFFS){
            Vec3d at=p.old.lerp(p.p,delta);float a=p.alpha*Math.max(0,Math.min(1,(p.life-p.age-delta)/Math.max(2f,p.life*.5f)));
            if(p.kind==FIRE_K)RenderUtil.billboard(m.peek(),b,camera,FIRE,true,at,p.size,(p.frame+p.age)&63,true,p.angle,p.r,p.g,p.b,a);
            else if(p.kind==GLOW_K)RenderUtil.billboard(m.peek(),b,camera,GLOW,true,at,p.size,0,false,0,p.r,p.g,p.b,a);
            else if(p.kind==SPARK_K)RenderUtil.beam(m.peek(),b,camera,SPARK,at,at.subtract(p.v.multiply(.035)),p.size,p.size*.25f,p.r,p.g,p.b,a);
        }
    }
}
