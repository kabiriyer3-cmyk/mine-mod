package com.hurma.topol.server;
import com.google.gson.*;
import com.hurma.topol.Topol;
import com.hurma.topol.entity.*;
import com.hurma.topol.net.Packets;
import com.hurma.topol.sim.GameRocket;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.*;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Server raycasts actual cursor + every projectile segment. No client-supplied impact coordinates. */
public final class FlightManager {
    public static final ChunkTicketType<Long> TICKET=ChunkTicketType.create("silo_work",Long::compare,100);
    private static final ArrayList<FlightData> FLIGHTS=new ArrayList<>();
    private static final ArrayList<CraterJob> JOBS=new ArrayList<>();
    private static final Map<UUID,Session> AIM=new HashMap<>();
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static Path saveFile;
    private static int clock;
    private static class Session {UUID silo;long flight;boolean fired,locked;int lockTicks;long touched;}
    private static class Save {int schema=2;ArrayList<FlightData> flights=new ArrayList<>();ArrayList<CraterJob> jobs=new ArrayList<>();}
    private record Trace(Vec3d point,boolean hit){}
    private FlightManager(){}
    public static void register(){
        ServerLifecycleEvents.SERVER_STARTED.register(FlightManager::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{
            save();
            for(CraterJob j:JOBS){var w=world(s,j.dimension);if(w!=null)j.release(w);}
            for(FlightData f:FLIGHTS){var w=world(s,f.dimension);if(w!=null)release(w,f);}
            FLIGHTS.clear();JOBS.clear();AIM.clear();saveFile=null;
        });
        ServerTickEvents.END_SERVER_TICK.register(FlightManager::tick);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->sync(handler.player));
    }
    private static ServerWorld world(MinecraftServer s,String dim){
        try{return s.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(dim)));}catch(Exception e){return null;}
    }
    private static LauncherEntity launcher(ServerPlayerEntity p,UUID id){
        for(Entity e:p.getServerWorld().iterateEntities())if(e instanceof LauncherEntity l&&id.equals(l.getUuid())&&!e.isRemoved())return l;
        return null;
    }
    public static boolean beginAim(ServerPlayerEntity p,ItemStack stack){
        UUID id=DesignatorItem.silo(stack);
        if(p.isSpectator()||id==null||!DesignatorItem.dimension(stack).equals(p.getServerWorld().getRegistryKey().getValue().toString())){
            LauncherEntity.message(p,"unbound");return false;
        }
        LauncherEntity l=launcher(p,id);
        if(l==null||!l.permitted(p)||p.squaredDistanceTo(l.getPos())>Topol.CONFIG.maxRange*Topol.CONFIG.maxRange){
            LauncherEntity.message(p,"unavailable");return false;
        }
        for(FlightData f:FLIGHTS)if(id.toString().equals(f.silo)){LauncherEntity.message(p,"inflight");return false;}
        if(!l.loaded()){LauncherEntity.message(p,"empty");return false;}
        Session s=new Session();s.silo=id;s.touched=p.getServerWorld().getTime();AIM.put(p.getUuid(),s);return true;
    }
    public static void endAim(ServerPlayerEntity p){AIM.remove(p.getUuid());}
    public static void aim(ServerPlayerEntity p,ItemStack stack,int used){
        if(used%2!=0)return;
        Session s=AIM.get(p.getUuid());
        if(s==null||!s.silo.equals(DesignatorItem.silo(stack)))return;
        ServerWorld w=p.getServerWorld();s.touched=w.getTime();
        if(!DesignatorItem.dimension(stack).equals(w.getRegistryKey().getValue().toString())){AIM.remove(p.getUuid());return;}
        if(p.isSpectator()){AIM.remove(p.getUuid());return;}
        Trace trace=cursor(w,p);
        FlightData active=null;
        for(FlightData f:FLIGHTS)if(f.id==s.flight&&s.fired)active=f;
        LauncherEntity l=s.fired?null:launcher(p,s.silo);
        Vec3d origin=active!=null?new Vec3d(active.lx,active.ly,active.lz):l!=null?l.launchBase():p.getPos();
        double distance=trace.point.distanceTo(origin);
        boolean valid=trace.hit&&distance>=Topol.CONFIG.minRange&&distance<=Topol.CONFIG.maxRange&&
                Math.abs(trace.point.y-origin.y)<128&&w.getWorldBorder().contains(BlockPos.ofFloored(trace.point));
        if(!s.fired&&l!=null&&l.permitted(p)&&valid){
            s.lockTicks+=2;
            if(s.lockTicks>=10){
                int pending=Topol.CONFIG.enableDestruction?JOBS.size():0;
                if(FLIGHTS.size()>=Topol.CONFIG.maxConcurrentFlights||pending+FLIGHTS.size()>=Topol.CONFIG.maxConcurrentBlasts+4){
                    LauncherEntity.message(p,"limit");s.lockTicks=0;
                }else if(!l.clearAbove()){LauncherEntity.message(p,"roof");s.lockTicks=0;}
                else if(l.consumeRound(p)){
                    FlightData f=new FlightData();f.dimension=w.getRegistryKey().getValue().toString();f.id=UUID.randomUUID().getLeastSignificantBits();
                    f.silo=s.silo.toString();f.operator=p.getUuidAsString();Vec3d start=l.launchBase();
                    f.x=f.lx=start.x;f.y=f.ly=start.y;f.z=f.lz=start.z;f.target(trace.point);f.destructive=Topol.CONFIG.enableDestruction;
                    FLIGHTS.add(f);s.flight=f.id;s.fired=true;active=f;
                    Packets.effect(w,0,l.mouth(),l.getYaw(),l.getId());Packets.effect(w,1,l.mouth(),0,f.id);
                    Packets.send(w,f.packet());save();
                }
            }
        }else if(!s.fired)s.lockTicks=0;
        if(active!=null&&valid&&active.operator.equals(p.getUuidAsString()))active.target(trace.point);
        ServerPlayNetworking.send(p,new Packets.Aim(trace.point,valid,active!=null,s.fired&&active==null?-1:s.lockTicks));
    }
    /** Never walks into unknown chunks just because a player points at the horizon. */
    private static Trace cursor(ServerWorld w,ServerPlayerEntity p){
        Vec3d start=p.getEyePos(),direction=p.getRotationVec(1),end=start;
        for(int d=4;d<=Topol.CONFIG.maxRange;d+=4){
            Vec3d candidate=start.add(direction.multiply(d));BlockPos b=BlockPos.ofFloored(candidate);
            if(candidate.y<w.getBottomY()||candidate.y>w.getTopY()||!w.isChunkLoaded(b.getX()>>4,b.getZ()>>4))break;
            end=candidate;
        }
        HitResult block=w.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        Vec3d hit=block.getPos();boolean found=block.getType()!=HitResult.Type.MISS;
        double best=start.squaredDistanceTo(hit);
        Box area=new Box(Math.min(start.x,end.x)-1,Math.min(start.y,end.y)-1,Math.min(start.z,end.z)-1,
                Math.max(start.x,end.x)+1,Math.max(start.y,end.y)+1,Math.max(start.z,end.z)+1);
        for(Entity entity:w.getOtherEntities(p,area)){
            if(!entity.canHit()||entity.isSpectator()||entity instanceof LauncherEntity)continue;
            Optional<Vec3d> point=entity.getBoundingBox().expand(.15).raycast(start,end);
            if(point.isPresent()&&start.squaredDistanceTo(point.get())<best){hit=point.get();best=start.squaredDistanceTo(hit);found=true;}
        }
        return new Trace(hit,found);
    }
    private static void tick(MinecraftServer server){
        clock++;
        AIM.entrySet().removeIf(e->{
            ServerPlayerEntity p=server.getPlayerManager().getPlayer(e.getKey());
            return p==null||!p.isUsingItem()||!p.getActiveItem().isOf(Topol.DESIGNATOR)||p.getServerWorld().getTime()-e.getValue().touched>5;
        });
        for(Iterator<FlightData> it=FLIGHTS.iterator();it.hasNext();){
            FlightData f=it.next();ServerWorld w=world(server,f.dimension);if(w==null)continue;
            var step=f.next();Vec3d from=f.pos(),next=new Vec3d(step.position().x(),step.position().y(),step.position().z());
            if(!ready(w,f,next)){
                if(++f.waiting>80){Packets.send(w,new Packets.Impact(f.dimension,f.id,from,0,0,0,false,0));release(w,f);it.remove();}
                continue;
            }
            f.waiting=0;
            HitResult block=w.raycast(new RaycastContext(from,next,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,ShapeContext.absent()));
            Vec3d impact=block.getPos();boolean hit=block.getType()!=HitResult.Type.MISS;
            double distance=hit?from.squaredDistanceTo(impact):Double.POSITIVE_INFINITY;
            if(f.age>GameRocket.CLEARANCE_TICKS){
                Box area=new Box(Math.min(from.x,next.x)-.3,Math.min(from.y,next.y)-.3,Math.min(from.z,next.z)-.3,
                        Math.max(from.x,next.x)+.3,Math.max(from.y,next.y)+.3,Math.max(from.z,next.z)+.3);
                for(Entity e:w.getOtherEntities(null,area)){
                    if(!e.canHit()||e.isSpectator()||e.getUuidAsString().equals(f.silo))continue;
                    var point=e.getBoundingBox().expand(.15).raycast(from,next);
                    if(point.isPresent()&&from.squaredDistanceTo(point.get())<distance){impact=point.get();distance=from.squaredDistanceTo(impact);hit=true;}
                }
                GameRocket.V a=new GameRocket.V(from.x,from.y,from.z),b=step.position(),t=new GameRocket.V(f.tx,f.ty,f.tz);
                double fraction=GameRocket.closestFraction(a,b,t);
                Vec3d nearest=from.lerp(next,fraction);
                if(nearest.squaredDistanceTo(f.target())<1&&from.squaredDistanceTo(nearest)<distance){hit=true;impact=nearest;}
            }
            boolean expired=f.age>=GameRocket.MAX_TICKS||next.y>w.getTopY()+64||next.y<w.getBottomY()-8||
                    next.distanceTo(new Vec3d(f.lx,f.ly,f.lz))>Topol.CONFIG.maxRange*1.5;
            if(hit||expired){
                boolean destroy=hit&&f.destructive&&Topol.CONFIG.enableDestruction;
                Vec3d at=hit?impact:from;
                if(destroy){
                    CraterJob job=CraterJob.create(f,at);JOBS.add(job);Packets.send(w,job.packet());
                }else Packets.send(w,new Packets.Impact(f.dimension,f.id,at,0,0,0,false,0));
                release(w,f);it.remove();continue;
            }
            f.x=next.x;f.y=next.y;f.z=next.z;f.vx=step.velocity().x();f.vy=step.velocity().y();f.vz=step.velocity().z();f.age++;
            Packets.send(w,f.packet());
        }
        long deadline=System.nanoTime()+Topol.CONFIG.blastMillisPerTick*1_000_000L;
        int active=Topol.CONFIG.enableDestruction?Math.min(JOBS.size(),Topol.CONFIG.maxConcurrentBlasts):0,n=0;
        for(Iterator<CraterJob> it=JOBS.iterator();it.hasNext()&&n<active;){
            CraterJob j=it.next();ServerWorld w=world(server,j.dimension);if(w==null)continue;n++;
            j.tick(w,deadline,Math.max(1,Topol.CONFIG.blockChangesPerTick/active),Math.max(1,Topol.CONFIG.blockChecksPerTick/active));
            if(j.complete()){Packets.send(w,new Packets.Wave(j.id,j.thermal,true));j.release(w);it.remove();}
        }
        if(clock%40==0)for(var p:server.getPlayerManager().getPlayerList())sync(p);
        if(clock%40==0)save();
    }
    private static boolean ready(ServerWorld w,FlightData f,Vec3d point){
        BlockPos p=BlockPos.ofFloored(point);ChunkPos cp=new ChunkPos(p.getX()>>4,p.getZ()>>4);
        if(!w.getWorldBorder().contains(p))return false;
        if(f.ticket!=null&&f.ticket.toLong()!=cp.toLong())w.getChunkManager().removeTicket(TICKET,f.ticket,2,f.id);
        f.ticket=cp;w.getChunkManager().addTicket(TICKET,cp,2,f.id);
        return w.isChunkLoaded(cp.x,cp.z);
    }
    private static void release(ServerWorld w,FlightData f){if(f.ticket!=null)w.getChunkManager().removeTicket(TICKET,f.ticket,2,f.id);}
    private static void sync(ServerPlayerEntity p){
        if(!ServerPlayNetworking.canSend(p,Packets.Flight.ID))return;
        String dim=p.getServerWorld().getRegistryKey().getValue().toString();
        for(FlightData f:FLIGHTS)if(dim.equals(f.dimension))ServerPlayNetworking.send(p,f.packet());
        if(Topol.CONFIG.enableDestruction)for(CraterJob j:JOBS)if(dim.equals(j.dimension)){
            ServerPlayNetworking.send(p,j.packet());ServerPlayNetworking.send(p,new Packets.Wave(j.id,j.wave,false));
        }
    }
    private static void load(MinecraftServer s){
        FLIGHTS.clear();JOBS.clear();AIM.clear();clock=0;
        saveFile=s.getSavePath(WorldSavePath.ROOT).resolve("solo-silo-state.json");
        if(!Files.exists(saveFile))return;
        try(Reader r=Files.newBufferedReader(saveFile)){
            Save data=GSON.fromJson(r,Save.class);if(data==null||data.schema!=2)throw new IOException("Unknown silo save schema");
            if(data.flights!=null)for(FlightData f:data.flights)if(f!=null&&f.valid()&&FLIGHTS.size()<16)FLIGHTS.add(f);
            if(data.jobs!=null)for(CraterJob j:data.jobs)if(j!=null&&j.valid()&&JOBS.size()<16)JOBS.add(j);
        }catch(Exception e){
            Topol.LOG.error("Could not restore silo state; keeping the original untouched",e);saveFile=null;
        }
    }
    private static void save(){
        if(saveFile==null)return;
        Save data=new Save();data.flights.addAll(FLIGHTS);data.jobs.addAll(JOBS);
        Path tmp=saveFile.resolveSibling(saveFile.getFileName()+".tmp");
        try{
            Files.writeString(tmp,GSON.toJson(data));
            if(Files.exists(saveFile))Files.copy(saveFile,saveFile.resolveSibling(saveFile.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
            try{Files.move(tmp,saveFile,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,saveFile,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException e){Topol.LOG.error("Cannot save silo state",e);}
    }
}
