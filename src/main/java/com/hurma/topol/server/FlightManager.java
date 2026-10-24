package com.hurma.topol.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hurma.topol.Topol;
import com.hurma.topol.entity.LauncherEntity;
import com.hurma.topol.net.Packets;
import com.hurma.topol.sim.FlightPath;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Server-authoritative world-independent flight clock and bounded terrain work queue. */
public final class FlightManager {
    public static final ChunkTicketType<Long> TICKET=ChunkTicketType.create("topol_work",Long::compare,200);
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final ArrayList<FlightData> FLIGHTS=new ArrayList<>();
    private static final ArrayList<CraterJob> JOBS=new ArrayList<>();
    private static Path saveFile;
    private static int ticks;
    private FlightManager() {}
    private static class Save {
        int schema=1;
        ArrayList<FlightData> flights=new ArrayList<>();
        ArrayList<CraterJob> jobs=new ArrayList<>();
    }
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(FlightManager::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{
            save();
            for(CraterJob j:JOBS){ServerWorld w=world(server,j.dimension);if(w!=null)j.release(w);}
            FLIGHTS.clear();JOBS.clear();saveFile=null;
        });
        ServerTickEvents.END_SERVER_TICK.register(FlightManager::tick);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->sync(handler.player));
    }
    public static boolean hasCapacity(){
        int jobs=Topol.CONFIG.enableDestruction?JOBS.size():0;
        return FLIGHTS.size()<Topol.CONFIG.maxConcurrentFlights&&jobs+FLIGHTS.size()<Topol.CONFIG.maxConcurrentBlasts+4;
    }
    private static ServerWorld world(MinecraftServer server,String dimension) {
        try {return server.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(dimension)));}
        catch(Exception e){return null;}
    }
    public static boolean launch(LauncherEntity launcher) {
        if(!hasCapacity()||!(launcher.getWorld() instanceof ServerWorld w))return false;
        int x=launcher.targetX(),z=launcher.targetZ();
        // Just the target chunk, not an entire route, is generated to resolve its surface.
        w.getChunk(x>>4,z>>4);
        int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);
        Vec3d l=launcher.launchBase();
        FlightData f=new FlightData();
        f.dimension=w.getRegistryKey().getValue().toString();f.id=UUID.randomUUID().getLeastSignificantBits();
        f.lx=l.x;f.ly=l.y;f.lz=l.z;f.tx=x+.5;f.ty=y;f.tz=z+.5;
        f.destructive=launcher.destructive();f.duration=FlightPath.durationTicks(Math.hypot(f.tx-f.lx,f.tz-f.lz));
        FLIGHTS.add(f);Packets.send(w,f.packet());save();
        Topol.LOG.info("Game flight launched in {} to {}, {}",f.dimension,x,z);
        return true;
    }
    private static void tick(MinecraftServer server) {
        ticks++;
        Iterator<FlightData> fi=FLIGHTS.iterator();
        while(fi.hasNext()) {
            FlightData f=fi.next();ServerWorld w=world(server,f.dimension);
            if(w==null)continue;
            f.age++;
            if(f.age>=f.duration-200) {
                w.getChunkManager().addTicket(TICKET,new ChunkPos((int)Math.floor(f.tx)>>4,(int)Math.floor(f.tz)>>4),2,f.id);
                f.ticketed=true;
            }
            if(f.age%40==0)Packets.send(w,f.packet());
            if(f.age>=f.duration) {
                boolean destructive=f.destructive&&Topol.CONFIG.enableDestruction;
                if(destructive&&JOBS.size()>=Topol.CONFIG.maxConcurrentBlasts+4){f.age=f.duration-1;continue;}
                int x=(int)Math.floor(f.tx),z=(int)Math.floor(f.tz);
                if(!w.isChunkLoaded(x>>4,z>>4)){f.age=f.duration-1;continue;}
                Vec3d at=new Vec3d(f.tx,w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z),f.tz);
                if(destructive) {
                    CraterJob j=CraterJob.create(f,at);JOBS.add(j);Packets.send(w,j.packet());
                } else Packets.send(w,new Packets.Impact(f.dimension,f.id,at,0,0,0,false,0));
                if(f.ticketed)w.getChunkManager().removeTicket(TICKET,new ChunkPos(x>>4,z>>4),2,f.id);
                fi.remove();
            }
        }
        long deadline=System.nanoTime()+Topol.CONFIG.blastMillisPerTick*1_000_000L;
        int active=Topol.CONFIG.enableDestruction?Math.min(JOBS.size(),Topol.CONFIG.maxConcurrentBlasts):0,visited=0;
        Iterator<CraterJob> ji=JOBS.iterator();
        while(ji.hasNext()&&visited<active) {
            CraterJob j=ji.next();ServerWorld w=world(server,j.dimension);
            if(w==null)continue;
            visited++;
            j.tick(w,deadline,Math.max(1,Topol.CONFIG.blockChangesPerTick/Math.max(1,active)),
                    Math.max(1,Topol.CONFIG.blockChecksPerTick/Math.max(1,active)));
            if(j.complete()) {
                Packets.send(w,new Packets.Wave(j.id,j.thermal,true));j.release(w);
                Topol.LOG.info("Crater finished: {} block changes",j.changed);ji.remove();
            }
        }
        // Re-sync after teleport/dimension change; the client ignores duplicates.
        if(ticks%100==0)for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList())sync(p);
        if(ticks%200==0)save();
    }
    private static void sync(ServerPlayerEntity p) {
        String dim=p.getServerWorld().getRegistryKey().getValue().toString();
        if(!ServerPlayNetworking.canSend(p,Packets.Flight.ID))return;
        for(FlightData f:FLIGHTS)if(dim.equals(f.dimension))ServerPlayNetworking.send(p,f.packet());
        for(CraterJob j:JOBS)if(Topol.CONFIG.enableDestruction&&dim.equals(j.dimension)) {
            ServerPlayNetworking.send(p,j.packet());
            ServerPlayNetworking.send(p,new Packets.Wave(j.id,j.wave,false));
        }
    }
    private static void start(MinecraftServer server) {
        FLIGHTS.clear();JOBS.clear();ticks=0;
        saveFile=server.getSavePath(WorldSavePath.ROOT).resolve("topol-flight-state.json");
        if(!Files.exists(saveFile))return;
        try(Reader r=Files.newBufferedReader(saveFile)) {
            Save s=GSON.fromJson(r,Save.class);
            if(s==null||s.schema!=1)throw new IOException("Unsupported Topol save schema");
            if(s.flights!=null)for(FlightData f:s.flights)if(f!=null&&f.valid()&&FLIGHTS.size()<32)FLIGHTS.add(f);
            if(s.jobs!=null)for(CraterJob j:s.jobs)if(j!=null&&j.valid()&&JOBS.size()<16)JOBS.add(j);
            Topol.LOG.info("Restored {} flights and {} crater jobs",FLIGHTS.size(),JOBS.size());
        } catch(Exception e) {
            Topol.LOG.error("Topol state cannot be read; preserving a copy. Check the world backup.",e);
            try {Files.copy(saveFile,saveFile.resolveSibling("topol-flight-state.unreadable-"+System.currentTimeMillis()+".json"));}
            catch(IOException ex){Topol.LOG.error("Could not preserve unreadable flight state",ex);saveFile=null;}
        }
    }
    private static void save() {
        if(saveFile==null)return;
        Save s=new Save();s.flights.addAll(FLIGHTS);s.jobs.addAll(JOBS);
        Path tmp=saveFile.resolveSibling(saveFile.getFileName()+".tmp");
        try {
            Files.writeString(tmp,GSON.toJson(s));
            if(Files.exists(saveFile))Files.copy(saveFile,saveFile.resolveSibling(saveFile.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
            try {Files.move(tmp,saveFile,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,saveFile,StandardCopyOption.REPLACE_EXISTING);}
        } catch(IOException e){Topol.LOG.error("Cannot save Topol flight state",e);}
    }
}
