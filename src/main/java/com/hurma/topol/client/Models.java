package com.hurma.topol.client;

import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import org.joml.*;
import java.lang.Math;

/**
 * Rigid-part animation for the converted artist models.
 * Missile space: tail (booster nozzle) at the origin, nose +Y, top -Z, 6.25 blocks long.
 * Silo space: entity position = ground surface, deck 0.25 above it, canister 7 blocks down.
 */
public final class Models {
    private Models(){}
    public static final float MISSILE_LENGTH=6.25f;
    /** Tail of the stowed round inside the canister, relative to the silo entity. */
    public static final double STOWED_TAIL=-6.6;
    public static final int BOOSTER_DROP=18;
    private static final String[] FINS={"fin0","fin1","fin2","fin3"};

    public static float smooth(float a,float b,float x){float t=Math.max(0f,Math.min(1f,(x-a)/(b-a)));return t*t*(3-2*t);}
    public static float fins(float age){return smooth(2,7,age);}
    public static float wings(float age){return smooth(BOOSTER_DROP,BOOSTER_DROP+7,age);}
    public static float intake(float age){return smooth(BOOSTER_DROP+2,BOOSTER_DROP+8,age);}

    public static void missile(MatrixStack m,VertexConsumerProvider b,int lod,int light,float fins,float wings,float intake,boolean booster){
        TMesh mesh=TMesh.get("missile");int o=OverlayTexture.DEFAULT_UV;
        mesh.emit("body",m,b,lod,light,o);
        if(booster)mesh.emit("booster",m,b,lod,light,o);
        m.push();m.translate(0,0,-.17f*(1-intake));mesh.emit("intake",m,b,lod,light,o);m.pop();
        float s=.06f+.94f*fins;
        for(String name:FINS){
            TMesh.Part p=mesh.part(name);if(p==null)continue;
            m.push();m.translate(p.pivot()[0],p.pivot()[1],p.pivot()[2]);
            // Folded fins: scale towards the hinge along the radial direction (they pop out of the body).
            Quaternionf q=new Quaternionf().rotationTo(1,0,0,p.axis()[0],p.axis()[1],p.axis()[2]);
            m.multiply(q);m.scale(s,1,1);m.multiply(new Quaternionf(q).conjugate());
            m.translate(-p.pivot()[0],-p.pivot()[1],-p.pivot()[2]);
            mesh.emit(name,m,b,lod,light,o);m.pop();
        }
        for(String name:new String[]{"wing_l","wing_r"}){
            TMesh.Part p=mesh.part(name);if(p==null)continue;
            float side=name.equals("wing_l")?1:-1;
            m.push();m.translate(p.pivot()[0],p.pivot()[1],p.pivot()[2]);
            m.multiply(RotationAxis.POSITIVE_Z.rotation((1-wings)*(float)(-Math.PI/2)*side));
            m.translate(-p.pivot()[0],-p.pivot()[1],-p.pivot()[2]);
            mesh.emit(name,m,b,lod,light,o);m.pop();
        }
    }
    public static void booster(MatrixStack m,VertexConsumerProvider b,int lod,int light){
        TMesh.get("missile").emit("booster",m,b,lod,light,OverlayTexture.DEFAULT_UV);
    }
    /** 0 closed .. 1 open (100 degrees about the hinge). */
    public static void silo(MatrixStack m,VertexConsumerProvider b,int lod,int light,float hatch){
        TMesh mesh=TMesh.get("silo");int o=OverlayTexture.DEFAULT_UV;
        for(String name:mesh.names()){
            if(!name.startsWith("hatch")){mesh.emit(name,m,b,lod,light,o);continue;}
            TMesh.Part p=mesh.part(name);
            m.push();m.translate(p.pivot()[0],p.pivot()[1],p.pivot()[2]);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(100*hatch));
            m.translate(-p.pivot()[0],-p.pivot()[1],-p.pivot()[2]);
            mesh.emit(name,m,b,lod,light,o);m.pop();
        }
    }
    /** Mesh +Y along forward, mesh -Z along top. Inputs need not be orthogonal. */
    public static Quaternionf orient(Vec3d forward,Vec3d top){
        Vector3f y=new Vector3f((float)forward.x,(float)forward.y,(float)forward.z).normalize();
        Vector3f u=new Vector3f((float)top.x,(float)top.y,(float)top.z);
        u.sub(new Vector3f(y).mul(u.dot(y)));
        if(u.lengthSquared()<1e-6f){
            if(Math.abs(y.y)<.9f)u.set(0f,1f,0f);else u.set(1f,0f,0f);
            u.sub(new Vector3f(y).mul(u.dot(y)));
        }
        u.normalize();
        Vector3f z=new Vector3f(u).negate(),x=new Vector3f(y).cross(z).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(x.x,x.y,x.z,y.x,y.y,y.z,z.x,z.y,z.z));
    }
}
