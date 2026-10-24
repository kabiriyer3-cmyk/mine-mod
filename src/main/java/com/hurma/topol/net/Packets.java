package com.hurma.topol.net;
import com.hurma.topol.Topol;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** Only server-to-client packets; targeting uses the native server item-use state. */
public final class Packets {
    private Packets(){}
    private static void vec(RegistryByteBuf b,Vec3d p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
    private static Vec3d vec(RegistryByteBuf b){return new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());}
    /** pos = booster nozzle (tail) of the missile; delay = ticks left in the canister before it moves. */
    public record Flight(String dimension,long id,Vec3d pos,Vec3d velocity,int age,int delay,float yaw) implements CustomPayload {
        public static final Id<Flight> ID=new Id<>(Topol.id("silo_flight"));
        public static final PacketCodec<RegistryByteBuf,Flight> CODEC=PacketCodec.of(Flight::write,Flight::read);
        private void write(RegistryByteBuf b){b.writeString(dimension,128);b.writeLong(id);vec(b,pos);vec(b,velocity);b.writeVarInt(age);b.writeVarInt(delay);b.writeFloat(yaw);}
        private static Flight read(RegistryByteBuf b){return new Flight(b.readString(128),b.readLong(),vec(b),vec(b),b.readVarInt(),b.readVarInt(),b.readFloat());}
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Aim(Vec3d point,boolean valid,boolean flying,int heldTicks) implements CustomPayload {
        public static final Id<Aim> ID=new Id<>(Topol.id("silo_aim"));
        public static final PacketCodec<RegistryByteBuf,Aim> CODEC=PacketCodec.of(Aim::write,Aim::read);
        private void write(RegistryByteBuf b){vec(b,point);b.writeBoolean(valid);b.writeBoolean(flying);b.writeVarInt(heldTicks);}
        private static Aim read(RegistryByteBuf b){return new Aim(vec(b),b.readBoolean(),b.readBoolean(),b.readVarInt());}
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
    public record Effect(int kind,Vec3d pos,float yaw,long seed) implements CustomPayload {
        public static final Id<Effect> ID=new Id<>(Topol.id("effect"));
        public static final PacketCodec<RegistryByteBuf,Effect> CODEC=PacketCodec.of(Effect::write,Effect::read);
        private void write(RegistryByteBuf b){b.writeByte(kind);vec(b,pos);b.writeFloat(yaw);b.writeLong(seed);}
        private static Effect read(RegistryByteBuf b){return new Effect(b.readUnsignedByte(),vec(b),b.readFloat(),b.readLong());}
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public static void register(){
        PayloadTypeRegistry.playS2C().register(Flight.ID,Flight.CODEC);
        PayloadTypeRegistry.playS2C().register(Aim.ID,Aim.CODEC);
        PayloadTypeRegistry.playS2C().register(Impact.ID,Impact.CODEC);
        PayloadTypeRegistry.playS2C().register(Wave.ID,Wave.CODEC);
        PayloadTypeRegistry.playS2C().register(Effect.ID,Effect.CODEC);
    }
    public static void send(ServerWorld w,CustomPayload packet){
        for(var p:w.getPlayers())if(ServerPlayNetworking.canSend(p,packet.getId()))ServerPlayNetworking.send(p,packet);
    }
    public static void effect(ServerWorld w,int kind,Vec3d p,float yaw,long seed){send(w,new Effect(kind,p,yaw,seed));}
}
