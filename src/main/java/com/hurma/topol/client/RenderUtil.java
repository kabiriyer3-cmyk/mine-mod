package com.hurma.topol.client;

import com.hurma.topol.Topol;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.joml.Vector3f;

public final class RenderUtil {
    private RenderUtil() {}
    public static Identifier fx(String name){return Topol.id("textures/fx/"+name+".png");}
    public record Projected(Vec3d point,double scale) {}
    public static Projected project(Vec3d p,Vec3d camera) {
        double d=p.distanceTo(camera);
        double limit=Math.max(64,Math.min(384,MinecraftClient.getInstance().options.getViewDistance().getValue()*13.0));
        double scale=d>limit?limit/d:1;
        return new Projected(camera.add(p.subtract(camera).multiply(scale)),scale);
    }
    public static int light(Vec3d p) {
        var w=MinecraftClient.getInstance().world;
        if(w==null)return 0xf000f0;
        BlockPos b=BlockPos.ofFloored(p);
        if(p.y>=w.getTopY()||!w.isChunkLoaded(b.getX()>>4,b.getZ()>>4))return 0xf000f0;
        return WorldRenderer.getLightmapCoordinates(w,b);
    }
    public static void billboard(MatrixStack.Entry entry,VertexConsumerProvider consumers,Camera camera,
                                 Identifier texture,boolean add,Vec3d point,float size,int frame,boolean atlas,
                                 float angle,float r,float g,float b,float alpha) {
        if(size<=0||alpha<=.001f)return;
        Projected pr=project(point,camera.getPos());
        float half=(float)(size*.5*pr.scale);
        Vector3f rr=new Vector3f(1,0,0).rotate(camera.getRotation()),uu=new Vector3f(0,1,0).rotate(camera.getRotation());
        Vec3d right=new Vec3d(rr),up=new Vec3d(uu);
        double co=Math.cos(angle),si=Math.sin(angle);
        Vec3d x=right.multiply(co*half).add(up.multiply(si*half));
        Vec3d y=up.multiply(co*half).subtract(right.multiply(si*half));
        Vec3d p=pr.point;
        float u0=0,v0=0,u1=1,v1=1;
        if(atlas){int f=frame&63;u0=(f%8)/8f;v0=(f/8)/8f;u1=u0+.125f;v1=v0+.125f;}
        quad(entry,consumers,camera,texture,add,
                new Vec3d[]{p.subtract(x).subtract(y),p.add(x).subtract(y),p.add(x).add(y),p.subtract(x).add(y)},
                new float[]{u0,v1,u1,v1,u1,v0,u0,v0},r,g,b,alpha);
    }
    public static void beam(MatrixStack.Entry entry,VertexConsumerProvider consumers,Camera camera,Identifier texture,
                            Vec3d from,Vec3d to,float width0,float width1,float r,float g,float b,float alpha) {
        Vec3d mid=from.add(to).multiply(.5);
        Projected pr=project(mid,camera.getPos());
        Vec3d a=pr.point.add(from.subtract(mid).multiply(pr.scale)),z=pr.point.add(to.subtract(mid).multiply(pr.scale));
        Vec3d side=z.subtract(a).crossProduct(camera.getPos().subtract(a)).normalize();
        if(side.lengthSquared()<.001)side=new Vec3d(new Vector3f(1,0,0).rotate(camera.getRotation()));
        Vec3d sx=side.multiply(width0*pr.scale*.5),ex=side.multiply(width1*pr.scale*.5);
        quad(entry,consumers,camera,texture,true,new Vec3d[]{a.subtract(sx),a.add(sx),z.add(ex),z.subtract(ex)},
                new float[]{0,0,1,0,1,1,0,1},r,g,b,alpha);
    }
    public static void ground(MatrixStack.Entry e,VertexConsumerProvider consumers,Camera camera,Identifier tex,
                              Vec3d center,float radius,boolean add,float r,float g,float b,float alpha) {
        Projected pr=project(center,camera.getPos());double s=radius*pr.scale;
        Vec3d p=pr.point;
        quad(e,consumers,camera,tex,add,new Vec3d[]{p.add(-s,0,-s),p.add(s,0,-s),p.add(s,0,s),p.add(-s,0,s)},
                new float[]{0,0,1,0,1,1,0,1},r,g,b,alpha);
    }
    private static void quad(MatrixStack.Entry e,VertexConsumerProvider consumers,Camera camera,Identifier texture,
                             boolean add,Vec3d[] p,float[] uv,float r,float g,float b,float alpha) {
        VertexConsumer vc=consumers.getBuffer(add?RenderLayer.getEyes(texture):RenderLayer.getEntityTranslucentEmissive(texture));
        Vec3d normal=p[1].subtract(p[0]).crossProduct(p[2].subtract(p[0])).normalize();
        boolean reverse=normal.dotProduct(camera.getPos().subtract(p[0]))<0;
        if(reverse)normal=normal.multiply(-1);
        float fade=add?alpha:1;
        int cr=channel(r*fade),cg=channel(g*fade),cb=channel(b*fade),ca=add?255:channel(alpha);
        for(int i=0;i<4;i++) {
            int k=reverse?3-i:i;
            Vec3d v=p[k].subtract(camera.getPos());
            vc.vertex(e.getPositionMatrix(),(float)v.x,(float)v.y,(float)v.z).color(cr,cg,cb,ca)
                    .texture(uv[k*2],uv[k*2+1]).overlay(OverlayTexture.DEFAULT_UV).light(0xf000f0)
                    .normal(e,(float)normal.x,(float)normal.y,(float)normal.z);
        }
    }
    private static int channel(float v){return Math.min(255,Math.max(0,(int)(v*255)));}
}
