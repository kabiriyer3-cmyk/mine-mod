package com.hurma.topol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.io.*;

/** Server owns gameplay settings. Client-only fields never authorize a server action. */
public final class TopolConfig {
    public boolean enableDestruction=true;
    public boolean ownerLock=true;
    public boolean breakUnbreakable=false;
    public int minRange=32, maxRange=1024;
    public int maxConcurrentFlights=4, maxConcurrentBlasts=2, maxLaunchersPerWorld=64;
    public int craterRadius=6, craterDepth=3, destructionRadius=16, thermalRadius=24;
    public int blockChangesPerTick=2500, blockChecksPerTick=12000, blastMillisPerTick=7;
    public int particleLimit=1600, meshDebrisLimit=24, effectsDistance=2400;
    public float effectDensity=1.0f, soundVolume=1.0f, cameraShake=0.5f, flashStrength=0.12f;
    public boolean enableFlash=false, enableCameraShake=true;

    public static TopolConfig load() {
        Path p=FabricLoader.getInstance().getConfigDir().resolve("solo-silo.json");
        Gson gson=new GsonBuilder().setPrettyPrinting().create();
        TopolConfig c=new TopolConfig();
        if(Files.isRegularFile(p)) {
            try(Reader r=Files.newBufferedReader(p)) {
                TopolConfig parsed=gson.fromJson(r,TopolConfig.class);
                if(parsed!=null)c=parsed;
            } catch(Exception e) {
                Topol.LOG.warn("Invalid topol.json; defaults used, original left untouched",e);
                return c;
            }
        }
        c.clamp();
        if(!Files.exists(p)) {
            try { Files.createDirectories(p.getParent()); Files.writeString(p,gson.toJson(c)); }
            catch(IOException e) { Topol.LOG.warn("Cannot write default Topol config",e); }
        }
        return c;
    }
    private static int clamp(int v,int a,int b) {return Math.max(a,Math.min(b,v));}
    private static float clamp(float v,float a,float b) {return Float.isFinite(v)?Math.max(a,Math.min(b,v)):a;}
    private void clamp() {
        minRange=clamp(minRange,24,128); maxRange=clamp(maxRange,minRange+1,2048);
        maxConcurrentFlights=clamp(maxConcurrentFlights,1,16); maxConcurrentBlasts=clamp(maxConcurrentBlasts,1,4);
        maxLaunchersPerWorld=clamp(maxLaunchersPerWorld,1,512);
        craterRadius=clamp(craterRadius,2,16); craterDepth=clamp(craterDepth,1,8);
        destructionRadius=clamp(destructionRadius,craterRadius,32);
        thermalRadius=clamp(thermalRadius,destructionRadius,48);
        blockChangesPerTick=clamp(blockChangesPerTick,100,10000);
        blockChecksPerTick=clamp(blockChecksPerTick,blockChangesPerTick,50000);
        blastMillisPerTick=clamp(blastMillisPerTick,1,20);
        particleLimit=clamp(particleLimit,100,8000); meshDebrisLimit=clamp(meshDebrisLimit,0,100);
        effectsDistance=clamp(effectsDistance,256,30000); effectDensity=clamp(effectDensity,.1f,1);
        soundVolume=clamp(soundVolume,0,1); cameraShake=clamp(cameraShake,0,1); flashStrength=clamp(flashStrength,0,1);
    }
}
