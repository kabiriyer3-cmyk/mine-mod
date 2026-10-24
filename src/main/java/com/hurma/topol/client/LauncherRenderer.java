package com.hurma.topol.client;

import com.hurma.topol.LauncherGeometry;
import com.hurma.topol.entity.LauncherEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.joml.Quaternionf;

public final class LauncherRenderer extends EntityRenderer<LauncherEntity> {
    public LauncherRenderer(EntityRendererFactory.Context c){super(c);shadowRadius=0;}
    @Override public Identifier getTexture(LauncherEntity e){return TMesh.texture("launcher",0);}
    @Override public void render(LauncherEntity e,float yaw,float delta,MatrixStack m,VertexConsumerProvider buffers,int light) {
        double dist=MinecraftClient.getInstance().gameRenderer.getCamera().getPos().distanceTo(e.getPos());
        TMesh mesh=TMesh.get("launcher",dist>120?1:0);
        Identifier texture=TMesh.texture("launcher",dist);
        m.push();m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-e.getYaw()));
        mesh.emit("body",m,buffers,texture,light,1);mesh.emit("wheels",m,buffers,texture,light,1);
        for(int i=0;i<4;i++) {
            m.push();m.translate(0,-.47*e.jacks(delta),0);mesh.emit("jack"+i,m,buffers,texture,light,1);m.pop();
        }
        double angle=e.deployment(delta)*Math.PI/2;
        m.push();
        m.translate(0,LauncherGeometry.HINGE_Y,LauncherGeometry.HINGE_Z);
        m.multiply(RotationAxis.POSITIVE_X.rotation((float)-angle));
        m.translate(0,-LauncherGeometry.HINGE_Y,-LauncherGeometry.HINGE_Z);
        mesh.emit("erector",m,buffers,texture,light,1);
        if(e.loaded()&&(e.state()!=LauncherEntity.LAUNCHING||e.stateTicks()+delta<20))
            mesh.emit("cap",m,buffers,texture,light,1);
        m.pop();
        double[] a=LauncherGeometry.RAM_A,b=LauncherGeometry.RAM_B;
        double dy=b[1]-LauncherGeometry.HINGE_Y,dz=b[2]-LauncherGeometry.HINGE_Z;
        double by=LauncherGeometry.HINGE_Y+Math.cos(angle)*dy+Math.sin(angle)*dz;
        double bz=LauncherGeometry.HINGE_Z-Math.sin(angle)*dy+Math.cos(angle)*dz;
        for(int sign:new int[]{-1,1}) {
            Vec3d start=new Vec3d(sign*a[0],a[1],a[2]),end=new Vec3d(sign*b[0],by,bz);
            Vec3d vector=end.subtract(start);double length=vector.length();
            m.push();m.translate(start.x,start.y,start.z);
            Vec3d v=vector.normalize();
            m.multiply(new Quaternionf().rotationTo(0,1,0,(float)v.x,(float)v.y,(float)v.z));
            segment(mesh,m,buffers,texture,light,"ram_tube",0,Math.min(3.0,length),.18f);
            double mid=Math.max(0,length-5.7);
            segment(mesh,m,buffers,texture,light,"ram_tube",mid,Math.min(2.9,length-mid),.145f);
            double rod=Math.max(0,length-2.7);
            segment(mesh,m,buffers,texture,light,"ram_rod",rod,length-rod,.105f);
            m.pop();
        }
        mesh.emit("lamps",m,buffers,texture,light,e.state()==LauncherEntity.STOWED?.3f:.9f);
        boolean active=e.state()==LauncherEntity.DEPLOYING||e.state()==LauncherEntity.STOWING||e.state()==LauncherEntity.LAUNCHING;
        float blink=active&&(e.age/5)%2==0?1:.16f;
        mesh.emit("beacons",m,buffers,texture,light,blink);
        m.pop();
        super.render(e,yaw,delta,m,buffers,light);
    }
    private void segment(TMesh mesh,MatrixStack m,VertexConsumerProvider b,Identifier tex,int light,String part,double offset,double length,float radius) {
        if(length<=.001)return;
        m.push();m.translate(0,offset,0);m.scale(radius,(float)length,radius);
        mesh.emit(part,m,b,tex,light,1);m.pop();
    }
}
