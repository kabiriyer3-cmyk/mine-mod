package com.hurma.topol.net;

import com.hurma.topol.Topol;
import com.hurma.topol.entity.LauncherEntity;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** Small bounded messages. No client packet can create a flight or a crater directly. */
public final class Packets {
    private Packets() {}
    private static void vec(RegistryByteBuf b,Vec3d p) {b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
    private static Vec3d vec(RegistryByteBuf b) {return new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());}

    public record Control(int entity,int action,int x,int z,int payload,long token) implements CustomPayload {
        public static final Id<Control> ID=new Id<>(Topol.id("control"));
        public static final PacketCodec<RegistryByteBuf,Control> CODEC=PacketCodec.of(Control::write,Control::read);
        private void write(RegistryByteBuf b) {b.writeVarInt(entity);b.writeByte(action);b.writeInt(x);b.writeInt(z);b.writeByte(payload);b.writeLong(token);}
        private static Control read(RegistryByteBuf b) {return new Control(b.readVarInt(),b.readUnsignedByte(),b.readInt(),b.readInt(),b.readUnsignedByte(),b.readLong());}
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Console(int entity,int result,long token,int minRange,int maxRange) implements CustomPayload {
        public static final Id<Console> ID=new Id<>(Topol.id("console"));
        public static final PacketCodec<RegistryByteBuf,Console> CODEC=PacketCodec.of(Console::write,Console::read);
        private void write(RegistryByteBuf b){b.writeVarInt(entity);b.writeByte(result);b.writeLong(token);b.writeVarInt(minRange);b.writeVarInt(maxRange);}
        private static Console read(RegistryByteBuf b){return new Console(b.readVarInt(),b.readUnsignedByte(),b.readLong(),b.readVarInt(),b.readVarInt());}
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Flight(String dimension,long id,Vec3d launch,Vec3d target,int age,int duration,boolean destructive) implements CustomPayload {
        public static final Id<Flight> ID=new Id<>(Topol.id("flight"));
        public static final PacketCodec<RegistryByteBuf,Flight> CODEC=PacketCodec.of(Flight::write,Flight::read);
        private void write(RegistryByteBuf b){b.writeString(dimension,128);b.writeLong(id);vec(b,launch);vec(b,target);b.writeVarInt(age);b.writeVarInt(duration);b.writeBoolean(destructive);}
        private static Flight read(RegistryByteBuf b){return new Flight(b.readString(128),b.readLong(),vec(b),vec(b),b.readVarInt(),b.readVarInt(),b.readBoolean());}
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Impact(String dimension,long id,Vec3d pos,int crater,int destruction,int thermal,boolean destructive,int age) implements CustomPayload {
        public static final Id<Impact> ID=new Id<>(Topol.id("impact"));
        public static final PacketCodec<RegistryByteBuf,Impact> CODEC=PacketCodec.of(Impact::write,Impact::read);
        private void write(RegistryByteBuf b){b.writeString(dimension,128);b.writeLong(id);vec(b,pos);b.writeVarInt(crater);b.writeVarInt(destruction);b.writeVarInt(thermal);b.writeBoolean(destructive);b.writeVarInt(age);}
        private static Impact read(RegistryByteBuf b){return new Impact(b.readString(128),b.readLong(),vec(b),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readVarInt());}
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Wave(long id,float radius,boolean finished) implements CustomPayload {
        public static final Id<Wave> ID=new Id<>(Topol.id("wave"));
        public static final PacketCodec<RegistryByteBuf,Wave> CODEC=PacketCodec.of(Wave::write,Wave::read);
        private void write(RegistryByteBuf b){b.writeLong(id);b.writeFloat(radius);b.writeBoolean(finished);}
        private static Wave read(RegistryByteBuf b){return new Wave(b.readLong(),b.readFloat(),b.readBoolean());}
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    /** 0 cap, 1 eject, 3 hydraulics, 4 lock, 5 siren. */
    public record Effect(int kind,Vec3d pos,float yaw,long seed) implements CustomPayload {
        public static final Id<Effect> ID=new Id<>(Topol.id("effect"));
        public static final PacketCodec<RegistryByteBuf,Effect> CODEC=PacketCodec.of(Effect::write,Effect::read);
        private void write(RegistryByteBuf b){b.writeByte(kind);vec(b,pos);b.writeFloat(yaw);b.writeLong(seed);}
        private static Effect read(RegistryByteBuf b){return new Effect(b.readUnsignedByte(),vec(b),b.readFloat(),b.readLong());}
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(Control.ID,Control.CODEC);
        PayloadTypeRegistry.playS2C().register(Console.ID,Console.CODEC);
        PayloadTypeRegistry.playS2C().register(Flight.ID,Flight.CODEC);
        PayloadTypeRegistry.playS2C().register(Impact.ID,Impact.CODEC);
        PayloadTypeRegistry.playS2C().register(Wave.ID,Wave.CODEC);
        PayloadTypeRegistry.playS2C().register(Effect.ID,Effect.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Control.ID,(p,ctx)->{
            ServerPlayerEntity player=ctx.player();
            ctx.server().execute(()->{
                if(player.isSpectator())return;
                var e=player.getServerWorld().getEntityById(p.entity());
                if(e instanceof LauncherEntity launcher && !launcher.isRemoved()) launcher.control(player,p);
            });
        });
    }
    public static void send(ServerWorld world,CustomPayload packet) {
        for(ServerPlayerEntity p:world.getPlayers()) if(ServerPlayNetworking.canSend(p,packet.getId())) ServerPlayNetworking.send(p,packet);
    }
    public static void console(ServerPlayerEntity player,int id,int result,long token) {
        ServerPlayNetworking.send(player,new Console(id,result,token,Topol.CONFIG.minRange,Topol.CONFIG.maxRange));
    }
    public static void effect(ServerWorld world,int kind,Vec3d p,float yaw,long seed) {
        send(world,new Effect(kind,p,yaw,seed));
    }
}
