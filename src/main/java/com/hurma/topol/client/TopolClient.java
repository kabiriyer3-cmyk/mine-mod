package com.hurma.topol.client;

import com.hurma.topol.Topol;
import com.hurma.topol.entity.LauncherEntity;
import com.hurma.topol.net.Packets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;

public final class TopolClient implements ClientModInitializer {
    private static ClientWorld lastWorld;
    @Override public void onInitializeClient() {
        EntityRendererRegistry.register(Topol.LAUNCHER,LauncherRenderer::new);
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){
            @Override public Identifier getFabricId(){return Topol.id("meshes");}
            @Override public void reload(ResourceManager m){TMesh.clear();}
        });
        ClientPlayNetworking.registerGlobalReceiver(Packets.Console.ID,(p,ctx)->ctx.client().execute(()->{
            prepareWorld();
            var mc=ctx.client();
            if(mc.currentScreen instanceof LauncherScreen s&&s.entity()==p.entity())s.response(p);
            else if(p.result()==0&&mc.world!=null&&mc.world.getEntityById(p.entity()) instanceof LauncherEntity e)
                mc.setScreen(new LauncherScreen(e,p));
        }));
        ClientPlayNetworking.registerGlobalReceiver(Packets.Flight.ID,(p,ctx)->ctx.client().execute(()->{prepareWorld();ClientFlights.receive(p);}));
        ClientPlayNetworking.registerGlobalReceiver(Packets.Impact.ID,(p,ctx)->ctx.client().execute(()->{prepareWorld();Effects.impact(p);}));
        ClientPlayNetworking.registerGlobalReceiver(Packets.Wave.ID,(p,ctx)->ctx.client().execute(()->{prepareWorld();Effects.wave(p);}));
        ClientPlayNetworking.registerGlobalReceiver(Packets.Effect.ID,(p,ctx)->ctx.client().execute(()->{prepareWorld();Effects.effect(p);}));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->clear());
        ClientTickEvents.END_CLIENT_TICK.register(TopolClient::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(ctx->{
            if(MinecraftClient.getInstance().world==null)return;
            MatrixStack m=ctx.matrixStack()==null?new MatrixStack():ctx.matrixStack();
            var buffers=MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            float delta=ctx.tickCounter().getTickDelta(false);
            m.push();
            ClientFlights.renderMeshes(m,buffers,ctx.camera(),delta);
            Effects.renderPieces(m,buffers,ctx.camera(),delta);
            buffers.draw();
            Effects.render(m,buffers,ctx.camera(),delta);
            ClientFlights.renderHot(m,buffers,ctx.camera(),delta);
            buffers.draw();m.pop();
        });
        HudRenderCallback.EVENT.register((context,tickCounter)->{
            ClientFlights.hud(context);ViewportFx.render(context);
        });
    }
    private static void tick(MinecraftClient mc) {
        prepareWorld();
        if(mc.world==null||mc.isPaused())return;
        SoundFx.tick();ClientFlights.tick();Effects.tick();ViewportFx.tick();
    }
    private static void prepareWorld() {
        var world=MinecraftClient.getInstance().world;
        if(world!=lastWorld){clear();lastWorld=world;}
    }
    public static boolean dimension(String name) {
        var w=MinecraftClient.getInstance().world;
        return w!=null&&w.getRegistryKey().getValue().toString().equals(name);
    }
    private static void clear(){ClientFlights.clear();Effects.clear();SoundFx.clear();ViewportFx.clear();lastWorld=null;}
}
