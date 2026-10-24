package com.hurma.topol.client;

import com.hurma.topol.*;
import com.hurma.topol.entity.LauncherEntity;
import com.hurma.topol.net.Packets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/**
 * Original billboard/mesh VFX renderer. Bounded pools, sorted alpha smoke, additive hot gas,
 * expanding dust front and a rising toroidal cloud. No vanilla ParticleManager or Explosion.
 */
public final class Effects {
    private static final ArrayList<Puff> PUFFS=new ArrayList<>();
    private static final ArrayList<Piece> PIECES=new ArrayList<>();
    private static final ArrayList<Decal> DECALS=new ArrayList<>();
    private static final LinkedHashMap<Long,Blast> BLASTS=new LinkedHashMap<>();
    private static final LinkedHashSet<Long> SEEN=new LinkedHashSet<>();
    private static final ArrayList<Smoke> SMOKE=new ArrayList<>();
    private static final Random RAND=new Random(73119);
    private static final Identifier SMOKE_TEX=RenderUtil.fx("smoke"),FIRE=RenderUtil.fx("fire"),
            GLOW=RenderUtil.fx("glow"),FLARE=RenderUtil.fx("flare"),RING=RenderUtil.fx("ring"),
            SCORCH=RenderUtil.fx("scorch"),SPARK=RenderUtil.fx("spark"),DEBRIS=RenderUtil.fx("debris");
    private Effects(){}
    private static final class Puff {
        Vec3d p,old,v;
        int kind,frame,age,life;
        float size,growth,r,g,b,opacity,angle,spin,buoyancy,drag;
        Puff(int kind,Vec3d pos,Vec3d velocity,float size,float growth,int life,float r,float g,float b,float opacity) {
            this.kind=kind;p=old=pos;v=velocity;this.size=size;this.growth=growth;this.life=life;
            this.r=r;this.g=g;this.b=b;this.opacity=opacity;
            frame=RAND.nextInt(64);angle=RAND.nextFloat()*6.283f;spin=(RAND.nextFloat()-.5f)*.012f;
            drag=kind==0?.988f:.98f;buoyancy=kind==0?.6f:kind==1?2:-9.8f;
        }
    }
    private record Smoke(Vec3d pos,float size,int frame,float angle,float r,float g,float b,float alpha) {}
    private static final class Decal {Vec3d p;float size;int age;Decal(Vec3d p,float size){this.p=p;this.size=size;}}
    private static final class Piece {
        String model;String[] parts;Vec3d p,old,v,pivot;Quaternionf base;float ax,ay,az,spin;int age;
        boolean grounded;
    }
    private static final class Blast {
        Packets.Impact packet;int age;float radius,goal,fade=1;boolean done,shaken;
        final float[][] seeds=new float[220][6];
        Blast(Packets.Impact packet) {
            this.packet=packet;age=packet.age();
            Random r=new Random(packet.id());
            for(float[] seed:seeds)for(int i=0;i<seed.length;i++)seed[i]=r.nextFloat();
        }
    }
    public static void clear(){PUFFS.clear();PIECES.clear();DECALS.clear();BLASTS.clear();SEEN.clear();SMOKE.clear();}
    private static boolean visible(Vec3d p) {
        var mc=MinecraftClient.getInstance();
        return mc.world!=null&&mc.gameRenderer.getCamera().getPos().squaredDistanceTo(p)<(double)Topol.CONFIG.effectsDistance*Topol.CONFIG.effectsDistance;
    }
    private static void add(Puff p) {
        if(PUFFS.size()<Topol.CONFIG.particleLimit&&visible(p.p)&&RAND.nextFloat()<=Topol.CONFIG.effectDensity)PUFFS.add(p);
    }
    public static void smoke(Vec3d p,Vec3d v,float size,float growth,int life,float r,float g,float b,float opacity) {
        add(new Puff(0,p,v,size,growth,life,r,g,b,opacity));
    }
    public static void fire(Vec3d p,Vec3d v,float size,int life) {add(new Puff(1,p,v,size,.04f,life,1,1,1,.85f));}
    public static void sparks(Vec3d p,int count,double speed) {
        for(int i=0;i<count;i++)add(new Puff(2,p,new Vec3d(RAND.nextGaussian()*speed,
                RAND.nextDouble()*speed,RAND.nextGaussian()*speed),.12f,0,25+RAND.nextInt(30),1,.72f,.25f,1));
    }
    public static void effect(Packets.Effect e) {
        if(!visible(e.pos()))return;
        switch(e.kind()) {
            case 0 -> {
                double a=Math.toRadians(e.yaw());
                Vec3d velocity=new Vec3d(-Math.sin(a)*9,13,Math.cos(a)*9);
                piece("launcher",new String[]{"cap"},e.pos(),velocity,
                        new Vec3d(0,LauncherGeometry.TPK_Y,LauncherGeometry.TPK_Z1+.2),
                        new Quaternionf().rotationY((float)-a).rotateX((float)-Math.PI/2),1.6f);
                SoundFx.play("cap",e.pos(),.7f,true);sparks(e.pos(),20,6);
                for(int i=0;i<16;i++)smoke(e.pos(),new Vec3d(RAND.nextGaussian()*2,2+RAND.nextDouble()*3,RAND.nextGaussian()*2),
                        1.3f,.07f,80,.8f,.8f,.74f,.7f);
            }
            case 1 -> {
                SoundFx.play("eject",e.pos(),1,true);
                for(int i=0;i<55;i++)smoke(e.pos(),new Vec3d(RAND.nextGaussian()*4,2+RAND.nextDouble()*8,RAND.nextGaussian()*4),
                        1.8f,.10f,140,.86f,.85f,.8f,.85f);
                double mouthHeight=LauncherGeometry.HINGE_Y+LauncherGeometry.TPK_Z1-LauncherGeometry.HINGE_Z;
                Vec3d base=e.pos().add(0,-mouthHeight,0);
                dust(base,45,9,1.1f);
                if(DECALS.size()<32)DECALS.add(new Decal(base.add(0,.02,0),5));
                double d=MinecraftClient.getInstance().gameRenderer.getCamera().getPos().distanceTo(base);
                ViewportFx.shake((float)(.28/(1+d/25)),18);
            }
            case 3 -> {
                var w=MinecraftClient.getInstance().world;
                if(w!=null&&w.getEntityById((int)e.seed()) instanceof LauncherEntity launcher)SoundFx.machine(launcher);
            }
            case 4 -> SoundFx.play("clunk",e.pos(),.65f,true);
            case 5 -> SoundFx.play("siren",e.pos(),.75f,true);
            default -> {}
        }
    }
    public static void ignition(Vec3d pos) {
        SoundFx.play("ignition",pos,1,true);
        for(int i=0;i<18;i++)fire(pos.add(random(1.5)),random(3),6,14+RAND.nextInt(10));
        sparks(pos,35,9);
    }
    public static void trail(Vec3d nozzle,Vec3d axis,Vec3d velocity,int stage,double time) {
        if(!visible(nozzle))return;
        double d=MinecraftClient.getInstance().gameRenderer.getCamera().getPos().distanceTo(nozzle);
        int n=d<180?4:d<600?2:1;
        double size=stage==0?2.8:stage==1?2.3:1.8;
        for(int i=0;i<n;i++) {
            double shift=i/(double)n;
            Vec3d p=nozzle.subtract(velocity.multiply(.05*shift)).subtract(axis.multiply(RAND.nextDouble()*5));
            Vec3d v=axis.multiply(-6-stage*2).add(random(1.4)).add(velocity.multiply(.06));
            smoke(p,v,(float)size,.085f,180+RAND.nextInt(100),.81f,.82f,.80f,.68f);
            fire(p,axis.multiply(-18).add(random(2)),(float)(size*1.4),10+RAND.nextInt(8));
        }
        if(time<8&&((int)(time*20))%4==0) {
            var w=MinecraftClient.getInstance().world;BlockPos b=BlockPos.ofFloored(nozzle);
            if(w!=null&&w.isChunkLoaded(b.getX()>>4,b.getZ()>>4)) {
                int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,b.getX(),b.getZ());
                if(nozzle.y-y<100)dust(new Vec3d(nozzle.x,y+.1,nozzle.z),7,12,1.4f);
            }
        }
    }
    public static void reentry(Vec3d p,Vec3d axis,Vec3d velocity) {
        fire(p.subtract(axis.multiply(1.8)),velocity.multiply(.04).add(axis.multiply(-12)),2.4f,12);
        if(RAND.nextFloat()<.4)smoke(p.subtract(axis.multiply(3)),velocity.multiply(.03),1.4f,.04f,75,.52f,.59f,.66f,.4f);
    }
    public static void dust(Vec3d p,int count,double speed,float size) {
        for(int i=0;i<count;i++) {
            double a=RAND.nextDouble()*Math.PI*2,r=RAND.nextDouble()*2;
            Vec3d dir=new Vec3d(Math.cos(a),0,Math.sin(a));
            Puff puff=new Puff(0,p.add(dir.multiply(r)).add(0,.3,0),dir.multiply(speed*(.25+RAND.nextDouble()*.75)).add(0,.6+RAND.nextDouble()*2,0),
                    size,.055f,100+RAND.nextInt(100),.64f,.57f,.43f,.7f);
            puff.buoyancy=.1f;puff.drag=.982f;add(puff);
        }
    }
    private static Vec3d random(double scale) {return new Vec3d(RAND.nextGaussian()*scale,RAND.nextGaussian()*scale,RAND.nextGaussian()*scale);}
    public static void piece(String model,String[] parts,Vec3d pos,Vec3d velocity,Vec3d pivot,Quaternionf attitude,float spin) {
        if(PIECES.size()>=Topol.CONFIG.meshDebrisLimit||!visible(pos))return;
        Piece p=new Piece();p.model=model;p.parts=parts;p.p=p.old=pos;p.v=velocity;p.pivot=pivot;p.base=new Quaternionf(attitude);
        p.ax=RAND.nextFloat()-.5f;p.ay=RAND.nextFloat()-.5f;p.az=RAND.nextFloat()-.5f;p.spin=spin;PIECES.add(p);
    }
    public static void impact(Packets.Impact packet) {
        if(!TopolClient.dimension(packet.dimension()))return;
        ClientFlights.remove(packet.id());
        Blast existing=BLASTS.get(packet.id());
        if(existing!=null){existing.age=Math.max(existing.age,packet.age());return;}
        if(!visible(packet.pos()))return;
        if(SEEN.contains(packet.id()))return;
        SEEN.add(packet.id());if(SEEN.size()>256)SEEN.remove(SEEN.iterator().next());
        if(packet.destructive()) {
            BLASTS.put(packet.id(),new Blast(packet));
            if(packet.age()<8) {
                SoundFx.blast(packet.pos());
                var mc=MinecraftClient.getInstance();
                var camera=mc.gameRenderer.getCamera();
                Vec3d eye=camera.getPos(),fireball=packet.pos().add(0,10,0);
                double d=eye.distanceTo(packet.pos());
                Vec3d forward=new Vec3d(new Vector3f(0,0,-1).rotate(camera.getRotation()));
                double facing=.25+.75*Math.max(0,forward.dotProduct(fireball.subtract(eye).normalize()));
                double cover=1;
                if(mc.world!=null&&mc.player!=null&&mc.world.raycast(new RaycastContext(eye,fireball,
                        RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player)).getType()!=HitResult.Type.MISS)cover=.12;
                ViewportFx.flash((float)(.95*facing*cover/(1+d/1200)));
                dust(packet.pos(),120,40,4);
                for(int i=0;i<90;i++) {
                    Puff p=new Puff(3,packet.pos().add(0,1,0),random(25).add(0,30,0),
                            .2f+RAND.nextFloat()*.7f,0,120+RAND.nextInt(100),.65f,.59f,.48f,1);
                    p.drag=.995f;add(p);
                }
            }
        } else {
            dust(packet.pos(),80,18,1.7f);
            SoundFx.play("sonic_boom",packet.pos(),.65f,true);
            sparks(packet.pos(),35,12);
        }
    }
    public static void wave(Packets.Wave p) {
        Blast b=BLASTS.get(p.id());
        if(b!=null){b.goal=Math.max(b.goal,p.radius());b.done=p.finished();}
    }
    public static void tick() {
        var world=MinecraftClient.getInstance().world;
        for(Iterator<Puff> it=PUFFS.iterator();it.hasNext();) {
            Puff p=it.next();p.old=p.p;p.age++;
            if(p.age>=p.life){it.remove();continue;}
            p.v=p.v.multiply(p.drag).add(.002,p.buoyancy*.05,.001);
            p.p=p.p.add(p.v.multiply(.05));p.size+=p.growth;p.angle+=p.spin;
            if((p.kind==2||p.kind==3)&&world!=null&&p.p.y<world.getTopY()) {
                BlockPos bp=BlockPos.ofFloored(p.p);
                if(world.isChunkLoaded(bp.getX()>>4,bp.getZ()>>4)&&!world.getBlockState(bp).getCollisionShape(world,bp).isEmpty())it.remove();
            }
        }
        for(Iterator<Piece> it=PIECES.iterator();it.hasNext();) {
            Piece p=it.next();p.old=p.p;p.age++;
            if(p.age>700){it.remove();continue;}
            if(!p.grounded) {
                p.v=p.v.multiply(.999).add(0,-9.8*.05,0);
                p.p=p.p.add(p.v.multiply(.05));
                if(world!=null&&p.p.y<world.getTopY()) {
                    BlockPos bp=BlockPos.ofFloored(p.p);
                    if(world.isChunkLoaded(bp.getX()>>4,bp.getZ()>>4)) {
                        double ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,bp.getX(),bp.getZ())+.2;
                        if(p.p.y<=ground) {
                            p.p=new Vec3d(p.p.x,ground,p.p.z);p.v=new Vec3d(p.v.x*.3,Math.abs(p.v.y)*.17,p.v.z*.3);
                            p.spin*=.25f;
                            if(p.v.lengthSquared()<2){p.v=Vec3d.ZERO;p.grounded=true;}
                            dust(p.p,8,4,.8f);
                        }
                    }
                }
            }
        }
        DECALS.removeIf(d->++d.age>1800);
        for(Iterator<Blast> it=BLASTS.values().iterator();it.hasNext();) {
            Blast b=it.next();b.age++;b.radius+=(b.goal-b.radius)*.35f;
            if(b.done)b.fade=Math.max(0,b.fade-.01f);
            if(!b.shaken) {
                double d=MinecraftClient.getInstance().gameRenderer.getCamera().getPos().distanceTo(b.packet.pos());
                if(d<=b.radius+2){ViewportFx.shake((float)(1.8/(1+d/100)),65);b.shaken=true;}
            }
            if(b.age>2400&&b.done)it.remove();
            else if(b.age>72000)it.remove();
        }
    }
    public static void renderPieces(MatrixStack m,VertexConsumerProvider consumers,Camera camera,float delta) {
        for(Piece p:PIECES) {
            Vec3d pos=p.old.lerp(p.p,delta);
            double d=pos.distanceTo(camera.getPos());
            if(d>Topol.CONFIG.effectsDistance)continue;
            RenderUtil.Projected pr=RenderUtil.project(pos,camera.getPos());
            m.push();Vec3d rel=pr.point().subtract(camera.getPos());m.translate(rel.x,rel.y,rel.z);
            m.scale((float)pr.scale(),(float)pr.scale(),(float)pr.scale());
            float a=(p.age+delta)*.05f*p.spin;
            m.multiply(new Quaternionf(p.base).rotateXYZ(a*p.ax,a*p.ay,a*p.az));
            m.translate(-p.pivot.x,-p.pivot.y,-p.pivot.z);
            TMesh mesh=TMesh.get(p.model,d>120?1:0);
            Identifier texture=TMesh.texture(p.model,d);
            for(String name:p.parts)mesh.emit(name,m,consumers,texture,RenderUtil.light(pos),1);
            m.pop();
        }
    }
    public static void render(MatrixStack m,VertexConsumerProvider consumers,Camera camera,float delta) {
        SMOKE.clear();
        var world=MinecraftClient.getInstance().world;
        float light=world==null?1:.28f+.72f*world.getSkyBrightness(delta);
        for(Puff p:PUFFS) {
            if(p.kind!=0)continue;
            float age=p.age+delta,u=age/p.life;
            float fade=Math.min(1,age/5)*Math.min(1,(1-u)*3);
            SMOKE.add(new Smoke(p.old.lerp(p.p,delta),p.size,p.frame,p.angle,p.r*light,p.g*light,p.b*light,p.opacity*fade));
        }
        for(Blast b:BLASTS.values())cloud(b,light,delta);
        SMOKE.sort(Comparator.comparingDouble((Smoke s)->s.pos.squaredDistanceTo(camera.getPos())).reversed());
        for(Smoke s:SMOKE)RenderUtil.billboard(m.peek(),consumers,camera,SMOKE_TEX,false,s.pos,s.size,s.frame,true,s.angle,s.r,s.g,s.b,s.alpha);
        for(Decal d:DECALS)RenderUtil.ground(m.peek(),consumers,camera,SCORCH,d.p,d.size,false,1,1,1,Math.min(1,(1800-d.age)/200f));
        for(Puff p:PUFFS) {
            if(p.kind==0)continue;
            Vec3d pos=p.old.lerp(p.p,delta);
            float fade=Math.min(1,(p.life-p.age-delta)/8)*p.opacity;
            if(p.kind==1)RenderUtil.billboard(m.peek(),consumers,camera,FIRE,true,pos,p.size,(p.frame+p.age*2)&63,true,p.angle,p.r,p.g,p.b,fade);
            else if(p.kind==2)RenderUtil.beam(m.peek(),consumers,camera,SPARK,pos,pos.subtract(p.v.multiply(.055)),p.size,p.size*.5f,1,.75f,.3f,fade);
            else RenderUtil.billboard(m.peek(),consumers,camera,DEBRIS,false,pos,p.size,0,false,p.angle,p.r*light,p.g*light,p.b*light,fade);
        }
        for(Blast b:BLASTS.values())hotCloud(b,m,consumers,camera,delta);
    }
    private static void cloud(Blast b,float light,float delta) {
        double time=(b.age+delta)*.05;
        if(time>120||!visible(b.packet.pos()))return;
        Vec3d origin=b.packet.pos();
        double scale=b.packet.crater()/64.0;
        double rise=(6+155*(1-Math.exp(-time/17)))*scale;
        double radius=(8+75*(1-Math.exp(-time/22)))*scale;
        float alpha=(float)(Math.min(1,time/1.8)*Math.min(1,(120-time)/25));
        float glow=(float)(Math.exp(-time/6)*.5);
        // Cap: a rolling torus with interior lobes. Positions evolve in 3D, not a single flat mushroom image.
        for(int i=0;i<130;i++) {
            float[] s=b.seeds[i];double a=s[0]*Math.PI*2+time*.025;
            double minor=s[1]*Math.PI*2+time*.1;
            double rr=radius*(.5+.5*s[2]) + Math.cos(minor)*radius*.14;
            Vec3d p=origin.add(Math.cos(a)*rr+time*.7*scale,rise+Math.sin(minor)*radius*.2+(s[3]-.5)*radius*.3,Math.sin(a)*rr+time*.18*scale);
            float size=(float)(radius*(.3+s[4]*.25)+8*scale);
            float shade=.64f+.32f*s[3];
            SMOKE.add(new Smoke(p,size,(int)(s[5]*63),s[2]*6.283f+(float)time*.015f,
                    light*shade+glow*.55f,light*shade*.93f+glow*.22f,light*shade*.82f,.7f*alpha));
        }
        // Stem narrows upward and bends with the same wind as the cap.
        for(int i=130;i<190;i++) {
            float[] s=b.seeds[i];double h=s[0],a=s[1]*Math.PI*2+time*.09;
            double rr=(7+radius*.15*(1-h))*scale*s[2];
            Vec3d p=origin.add(Math.cos(a)*rr+time*.7*h*scale,rise*h,Math.sin(a)*rr+time*.18*h*scale);
            float size=(float)(12*scale+radius*(.16+.18*(1-h)));
            SMOKE.add(new Smoke(p,size,(int)(s[5]*63),s[2]*6.283f,light*.55f+glow*.6f,light*.51f+glow*.22f,light*.43f,.72f*alpha));
        }
        if(b.radius>1&&b.fade>0) {
            for(int i=0;i<64;i++) {
                double a=i/64.0*Math.PI*2;
                Vec3d p=origin.add(Math.cos(a)*b.radius,.8+Math.sin(a*7+time)*.7,Math.sin(a)*b.radius);
                SMOKE.add(new Smoke(p,8+b.radius*.08f,i,0,light*.65f,light*.59f,light*.48f,b.fade*.7f));
            }
        }
    }
    private static void hotCloud(Blast b,MatrixStack m,VertexConsumerProvider consumers,Camera camera,float delta) {
        if(!visible(b.packet.pos()))return;
        double t=(b.age+delta)*.05;
        Vec3d o=b.packet.pos();
        float scale=b.packet.crater()/64f;
        float heat=(float)Math.exp(-t/3.6);
        if(t<17) {
            float size=(float)((8+70*(1-Math.exp(-t/1.3)))*scale);
            Vec3d p=o.add(0,Math.min(40*scale,t*7*scale)+size*.25,0);
            RenderUtil.billboard(m.peek(),consumers,camera,FIRE,true,p,size,(int)(t*17)&63,true,.1f,1,1,1,Math.min(1,heat*1.6f));
            RenderUtil.billboard(m.peek(),consumers,camera,GLOW,true,p,size*2.5f,0,false,0,1,.7f,.35f,heat*.7f);
            if(t<2)RenderUtil.billboard(m.peek(),consumers,camera,FLARE,true,p,size*4,0,false,0,1,.97f,.8f,(float)Math.exp(-t*2));
        }
        if(b.radius>1&&b.fade>0)RenderUtil.ground(m.peek(),consumers,camera,RING,o.add(0,.4,0),b.radius*1.06f,true,.8f,.78f,.67f,b.fade*.42f);
    }
}
