package com.hurma.topol.server;

import com.hurma.topol.Topol;
import com.hurma.topol.net.Packets;
import com.hurma.topol.sim.BlastShape;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import java.util.*;

/**
 * Radially ordered, resumable block edits. No Explosion object, vanilla particles or block drops.
 * A bounded set of short-lived tickets loads the ring currently being processed.
 */
public final class CraterJob {
    public String dimension;
    public long id;
    public int cx,cy,cz,crater,depth,destruction,thermal,age;
    public int cursor,currentY=Integer.MIN_VALUE,low,top;
    public float wave;
    public long changed;
    public ArrayList<String> hitEntities=new ArrayList<>();
    private transient long[] columns;
    private transient LinkedHashMap<Long,ChunkPos> tickets;
    private transient HashSet<String> hit;
    private static final int FLAGS=Block.NOTIFY_LISTENERS|Block.FORCE_STATE|Block.SKIP_DROPS;

    public static CraterJob create(FlightData f,Vec3d impact) {
        CraterJob j=new CraterJob();
        j.id=f.id;j.dimension=f.dimension;j.cx=(int)Math.floor(impact.x);j.cy=(int)Math.floor(impact.y);j.cz=(int)Math.floor(impact.z);
        j.crater=Topol.CONFIG.craterRadius;j.depth=Topol.CONFIG.craterDepth;
        j.destruction=Topol.CONFIG.destructionRadius;j.thermal=Topol.CONFIG.thermalRadius;
        return j;
    }
    public boolean valid() {
        return dimension!=null&&dimension.length()<128&&crater>=2&&crater<=16&&depth>=1&&depth<=8&&
                destruction>=crater&&destruction<=32&&thermal>=destruction&&thermal<=48&&cursor>=0&&
                cursor<=(2*thermal+1)*(2*thermal+1)&&Math.abs((long)cx)<30000000&&Math.abs((long)cz)<30000000&&
                Math.abs((long)cy)<4096&&age>=0&&Float.isFinite(wave)&&
                (currentY==Integer.MIN_VALUE||Math.abs((long)currentY)<4096)&&
                (hitEntities==null||hitEntities.size()<=8192);
    }
    private void init() {
        if(columns!=null)return;
        columns=BlastShape.columns(thermal);
        cursor=Math.min(cursor,columns.length);
        tickets=new LinkedHashMap<>();
        if(hitEntities==null)hitEntities=new ArrayList<>();
        hit=new HashSet<>(hitEntities);
    }
    public Vec3d center(){return new Vec3d(cx+.5,cy,cz+.5);}
    public Packets.Impact packet(){return new Packets.Impact(dimension,id,center(),crater,destruction,thermal,true,age);}
    public boolean complete(){init();return cursor>=columns.length;}

    public void tick(ServerWorld w,long deadline,int maxChanges,int maxChecks) {
        init();age++;
        int edits=0,checks=0;
        BlockPos.Mutable pos=new BlockPos.Mutable();
        while(cursor<columns.length&&edits<maxChanges&&checks<maxChecks&&System.nanoTime()<deadline) {
            long key=columns[cursor];
            int dx=BlastShape.x(key),dz=BlastShape.z(key),x=cx+dx,z=cz+dz;
            double d=BlastShape.distance(key);
            // Game wave speed is deliberately modest. Processing may lag, never run ahead.
            if(d>age*4.0)break;
            if(!ensureChunk(w,x>>4,z>>4))break;
            if(!w.getWorldBorder().contains(new BlockPos(x,cy,z))){next(d);continue;}
            boolean bowl=d<=crater;
            boolean lip=!bowl&&d<crater*1.13;
            if(currentY==Integer.MIN_VALUE) {
                top=w.getTopY(Heightmap.Type.WORLD_SURFACE,x,z);
                if(bowl) {
                    low=Math.max(w.getBottomY()+1,BlastShape.floor(cy,d,crater,depth,dx,dz,id)-1);
                    currentY=Math.max(low,Math.min(w.getTopY()-1,Math.min(top,cy+destruction)));
                } else if(lip) {
                    low=Math.max(w.getBottomY()+1,top-1);
                    currentY=Math.min(w.getTopY()-1,Math.min(cy+1,top+1));
                } else {
                    low=Math.max(w.getBottomY()+1,cy-4);
                    currentY=Math.min(w.getTopY()-1,Math.min(cy+destruction,Math.max(top,low)));
                }
            }
            while(currentY>=low&&checks<maxChecks&&edits<maxChanges&&System.nanoTime()<deadline) {
                pos.set(x,currentY,z);
                BlockState old=w.getBlockState(pos),replacement=null;
                checks++;
                if(!protectedBlock(w,pos,old)) {
                    double n=BlastShape.noise(x,currentY*31+z,id);
                    double radial=Math.sqrt(d*d+(currentY-cy)*(double)(currentY-cy));
                    if(bowl&&Math.abs(currentY-cy)<=destruction) {
                        if(currentY>low+1) {if(!old.isAir())replacement=Blocks.AIR.getDefaultState();}
                        else if(currentY<top)replacement=(n>.6?Blocks.COARSE_DIRT:Topol.SCORCHED_EARTH).getDefaultState();
                    } else if(lip&&Math.abs(currentY-cy)<depth+1) {
                        replacement=(n>.6?Blocks.COARSE_DIRT:Topol.SCORCHED_EARTH).getDefaultState();
                    } else if(!old.isAir()) {
                        if(radial<=destruction) {
                            double strength=4+70*Math.pow(1-radial/destruction,2);
                            if(old.getBlock().getBlastResistance()<strength)replacement=Blocks.AIR.getDefaultState();
                        } else if(radial<=thermal&&(old.isIn(BlockTags.LEAVES)||old.isIn(BlockTags.SAPLINGS))) {
                            if(n<(thermal-d)/Math.max(1.0,thermal-destruction))replacement=Blocks.AIR.getDefaultState();
                        } else if(radial<=thermal&&currentY==top-1 && n<.35*(thermal-d)/thermal && old.getFluidState().isEmpty()) {
                            replacement=Topol.SCORCHED_EARTH.getDefaultState();
                        }
                    }
                }
                if(replacement!=null&&!replacement.equals(old)) {
                    // Container callbacks must not scatter inventories into thousands of dropped items.
                    if(w.getBlockEntity(pos)!=null)w.removeBlockEntity(pos);
                    if(w.setBlockState(pos,replacement,FLAGS)){edits++;changed++;}
                }
                currentY--;
            }
            if(currentY<low)next(d);
        }
        if(age%5==0||complete()) {
            hurtReached(w);
            Packets.send(w,new Packets.Wave(id,wave,complete()));
        }
    }
    private boolean protectedBlock(ServerWorld w,BlockPos p,BlockState b) {
        if(b.isOf(Blocks.BARRIER)||b.isOf(Blocks.COMMAND_BLOCK)||b.isOf(Blocks.CHAIN_COMMAND_BLOCK)||
                b.isOf(Blocks.REPEATING_COMMAND_BLOCK)||b.isOf(Blocks.STRUCTURE_BLOCK)||b.isOf(Blocks.JIGSAW))return true;
        return !Topol.CONFIG.breakUnbreakable&&b.getHardness(w,p)<0;
    }
    private void next(double d){wave=(float)d;cursor++;currentY=Integer.MIN_VALUE;}
    private boolean ensureChunk(ServerWorld w,int x,int z) {
        ChunkPos p=new ChunkPos(x,z);long key=p.toLong();
        if(!tickets.containsKey(key)) {
            tickets.put(key,p);
            w.getChunkManager().addTicket(FlightManager.TICKET,p,2,id);
            if(tickets.size()>25) {
                var it=tickets.entrySet().iterator();var first=it.next();it.remove();
                w.getChunkManager().removeTicket(FlightManager.TICKET,first.getValue(),2,id);
            }
        }
        // Refresh timeout while working on the same column.
        w.getChunkManager().addTicket(FlightManager.TICKET,p,2,id);
        return w.isChunkLoaded(x,z);
    }
    private void hurtReached(ServerWorld w) {
        double reach=Math.min(wave,destruction);
        Vec3d c=center();
        for(Entity e:w.getOtherEntities(null,new Box(c.x-reach,c.y-reach,c.z-reach,c.x+reach,c.y+reach,c.z+reach))) {
            if(e.isSpectator()||e instanceof PlayerEntity p&&p.getAbilities().creativeMode)continue;
            String uuid=e.getUuidAsString();
            double distance=e.getPos().distanceTo(c);
            if(distance>reach||hit.contains(uuid))continue;
            hit.add(uuid);
            if(hitEntities.size()<8192)hitEntities.add(uuid);
            if(!(e instanceof LivingEntity)&&!(e instanceof com.hurma.topol.entity.LauncherEntity)) {
                e.discard(); // loose items, vehicles and primed TNT: no secondary vanilla explosion/drop storm
                continue;
            }
            float amount=(float)(300*Math.pow(Math.max(0,1-distance/destruction),2));
            e.damage(w.getDamageSources().explosion(null,null),amount);
            Vec3d push=e.getPos().subtract(c).normalize().multiply(2.5*Math.max(0,1-distance/destruction));
            e.addVelocity(push.x,Math.max(.08,push.y+.35),push.z);e.velocityModified=true;
        }
    }
    public void release(ServerWorld w) {
        if(tickets!=null)for(ChunkPos p:tickets.values())w.getChunkManager().removeTicket(FlightManager.TICKET,p,2,id);
        if(tickets!=null)tickets.clear();
    }
}
