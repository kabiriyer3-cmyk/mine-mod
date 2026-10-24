package com.hurma.topol.server;

import com.hurma.topol.net.Packets;
import net.minecraft.util.math.Vec3d;

/** Save-compatible data only. Game flight does not keep hundreds of chunks loaded. */
public final class FlightData {
    public String dimension;
    public long id;
    public double lx,ly,lz,tx,ty,tz;
    public int age,duration;
    public boolean destructive;
    public transient boolean ticketed;
    public Vec3d launch(){return new Vec3d(lx,ly,lz);}
    public Vec3d target(){return new Vec3d(tx,ty,tz);}
    public Packets.Flight packet(){return new Packets.Flight(dimension,id,launch(),target(),age,duration,destructive);}
    public boolean valid() {
        return dimension!=null&&dimension.length()<128&&duration>=1600&&duration<=5000&&age>=0&&age<=duration+20&&
                Double.isFinite(lx+ly+lz+tx+ty+tz)&&Math.abs(lx)<3e7&&Math.abs(lz)<3e7&&Math.abs(tx)<3e7&&Math.abs(tz)<3e7&&
                Math.abs(ly)<4096&&Math.abs(ty)<4096;
    }
}
