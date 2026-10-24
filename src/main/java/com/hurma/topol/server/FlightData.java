package com.hurma.topol.server;
import com.hurma.topol.net.Packets;
import com.hurma.topol.sim.GameRocket;
import net.minecraft.util.math.Vec3d;

public final class FlightData {
    public String dimension,silo,operator;
    public long id;
    public double x,y,z,vx,vy=2,vz,tx,ty,tz,lx,ly,lz;
    public int age,waiting,delay;
    public float yaw;
    public boolean destructive;
    public transient net.minecraft.util.math.ChunkPos ticket;
    public Vec3d pos(){return new Vec3d(x,y,z);}
    public Vec3d velocity(){return new Vec3d(vx,vy,vz);}
    public Vec3d target(){return new Vec3d(tx,ty,tz);}
    public Packets.Flight packet(){return new Packets.Flight(dimension,id,pos(),velocity(),age,delay,yaw);}
    public GameRocket.Step next(){
        return GameRocket.step(new GameRocket.V(x,y,z),new GameRocket.V(vx,vy,vz),new GameRocket.V(tx,ty,tz),age);
    }
    public void target(Vec3d p){tx=p.x;ty=p.y;tz=p.z;}
    public boolean valid(){
        return dimension!=null&&silo!=null&&operator!=null&&dimension.length()<128&&silo.length()<64&&operator.length()<64&&
                Double.isFinite(x+y+z+vx+vy+vz+tx+ty+tz+lx+ly+lz)&&Math.abs(x)<3e7&&Math.abs(z)<3e7&&
                Math.abs(y)<4096&&Math.abs(tx)<3e7&&Math.abs(tz)<3e7&&Math.abs(ty)<4096&&age>=0&&age<=GameRocket.MAX_TICKS&&delay>=0&&delay<=60&&Float.isFinite(yaw);
    }
}
