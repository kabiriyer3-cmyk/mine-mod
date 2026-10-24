package com.hurma.topol.client;
import com.hurma.topol.Topol;
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
    @Override public void onInitializeClient(){
        EntityRendererRegistry.register(Topol.LAUNCHER,LauncherRenderer::new);
        BuiltinItemRendererRegistry.INSTANCE.register(Topol.DESIGNATOR,new DesignatorRenderer());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){
            @Override public Identifier getFabricId(){return Topol.id("silo_meshes");}
            @Override public void reload(ResourceManager m){TMesh.clear();}
        });
        ClientPlayNetworking.registerGlobalReceiver(Packets.Flight.ID,(p,ctx)->ctx.client().execute(()->{prepare();ClientFlights.receive(p);}));
        ClientPlayNetworking.registerGlobalReceiver(Packets.Aim.ID,(p,ctx)->ctx.client().execute(()->{prepare();DesignatorHud.receive(p);}));
        ClientPlayNetworking.registerGlobalReceiver(Packets.Impact.ID,(p,ctx)->ctx.client().execute(()->{prepare();Effects.impact(p);}));
        ClientPlayNetworking.registerGlobalReceiver(Packets.Wave.ID,(p,ctx)->ctx.client().execute(()->{prepare();Effects.wave(p);}));
        ClientPlayNetworking.registerGlobalReceiver(Packets.Effect.ID,(p,ctx)->ctx.client().execute(()->{prepare();Effects.effect(p);}));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->clear());
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            prepare();if(mc.world==null||mc.isPaused())return;
            ClientFlights.tick();Effects.tick();SoundFx.tick();ViewportFx.tick();DesignatorHud.tick();
        });
        WorldRenderEvents.AFTER_ENTITIES.register(ctx->{
            if(MinecraftClient.getInstance().world==null)return;
            MatrixStack m=ctx.matrixStack()==null?new MatrixStack():ctx.matrixStack();
            var b=MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            float delta=ctx.tickCounter().getTickDelta(false);m.push();
            ClientFlights.renderMeshes(m,b,ctx.camera(),delta);Effects.renderPieces(m,b,ctx.camera(),delta);b.draw();
            Effects.render(m,b,ctx.camera(),delta);ClientFlights.renderHot(m,b,ctx.camera(),delta);
            DesignatorHud.render(m,b,ctx.camera());b.draw();m.pop();
        });
        HudRenderCallback.EVENT.register((c,t)->DesignatorHud.hud(c));
    }
    public static boolean dimension(String name){var w=MinecraftClient.getInstance().world;return w!=null&&w.getRegistryKey().getValue().toString().equals(name);}
    private static void prepare(){var w=MinecraftClient.getInstance().world;if(w!=lastWorld){clear();lastWorld=w;}}
    private static void clear(){ClientFlights.clear();Effects.clear();SoundFx.clear();ViewportFx.clear();DesignatorHud.clear();lastWorld=null;}
}
