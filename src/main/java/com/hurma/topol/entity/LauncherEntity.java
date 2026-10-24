package com.hurma.topol.entity;

import com.hurma.topol.*;
import com.hurma.topol.net.Packets;
import com.hurma.topol.server.FlightManager;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.UUID;

/** A stationary, owned launcher. The client only draws tracked state; all actions are checked here. */
public final class LauncherEntity extends Entity {
    public static final int STOWED=0, DEPLOYING=1, READY=2, LAUNCHING=3, EMPTY=4, STOWING=5;
    public static final int DEPLOY_TICKS=400, STOW_TICKS=300, LAUNCH_TICKS=60;
    private static final TrackedData<Integer> STATE=DataTracker.registerData(LauncherEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> START=DataTracker.registerData(LauncherEntity.class,TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Boolean> LOADED=DataTracker.registerData(LauncherEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> TX=DataTracker.registerData(LauncherEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> TZ=DataTracker.registerData(LauncherEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> DESTRUCTIVE=DataTracker.registerData(LauncherEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private UUID owner,armedBy;
    private long token,armedUntil,lastCommand=-100;
    private boolean capSent;

    public LauncherEntity(EntityType<? extends LauncherEntity> type,World world) {
        super(type,world);
        ignoreCameraFrustum=true; setNoGravity(true);
    }
    @Override protected void initDataTracker(DataTracker.Builder b) {
        b.add(STATE,STOWED);b.add(START,0L);b.add(LOADED,false);b.add(TX,0);b.add(TZ,0);b.add(DESTRUCTIVE,true);
    }
    public int state(){return dataTracker.get(STATE);}
    public int stateTicks(){return (int)Math.max(0,Math.min(100000,getWorld().getTime()-dataTracker.get(START)));}
    public boolean loaded(){return dataTracker.get(LOADED);}
    public int targetX(){return dataTracker.get(TX);}
    public int targetZ(){return dataTracker.get(TZ);}
    public boolean destructive(){return dataTracker.get(DESTRUCTIVE);}
    public void setOwner(UUID id){owner=id;}
    @Override public void setYaw(float yaw) {
        super.setYaw(yaw);
        setBoundingBox(calculateBoundingBox());
    }
    public boolean permitted(PlayerEntity p) {
        return !Topol.CONFIG.ownerLock || owner==null || owner.equals(p.getUuid()) || p.hasPermissionLevel(2);
    }
    private void setState(int s) {
        dataTracker.set(STATE,s);dataTracker.set(START,getWorld().getTime());capSent=false;
        setBoundingBox(calculateBoundingBox());
    }
    public double deployment(float delta) {
        double ticks=stateTicks()+delta;
        return switch(state()) {
            case DEPLOYING -> com.hurma.topol.sim.FlightPath.smooth((ticks-35)/340);
            case STOWING -> 1-com.hurma.topol.sim.FlightPath.smooth(ticks/260);
            case READY,LAUNCHING,EMPTY -> 1;
            default -> 0;
        };
    }
    public double jacks(float delta) {
        return switch(state()) {
            case DEPLOYING -> Math.min(1,(stateTicks()+delta)/40.0);
            case STOWING -> Math.min(1,Math.max(0,(STOW_TICKS-stateTicks()-delta)/40.0));
            case READY,LAUNCHING,EMPTY -> 1;
            default -> 0;
        };
    }
    public Vec3d local(double x,double y,double z) {
        double a=Math.toRadians(getYaw()),c=Math.cos(a),s=Math.sin(a);
        return getPos().add(c*x-s*z,y,s*x+c*z);
    }
    public Vec3d launchBase() {
        return local(0,LauncherGeometry.HINGE_Y+LauncherGeometry.MISSILE_BASE_Z-LauncherGeometry.HINGE_Z,
                LauncherGeometry.HINGE_Z-(LauncherGeometry.TPK_Y-LauncherGeometry.HINGE_Y));
    }
    public Vec3d mouth(){return launchBase().add(0,LauncherGeometry.TPK_Z1-LauncherGeometry.MISSILE_BASE_Z,0);}
    @Override protected Box calculateBoundingBox() {
        double a=Math.toRadians(getYaw()),c=Math.abs(Math.cos(a)),s=Math.abs(Math.sin(a));
        double hx=c*2.4+s*13.8,hz=s*2.4+c*13.8;
        return new Box(getX()-hx,getY(),getZ()-hz,getX()+hx,getY()+4.8,getZ()+hz);
    }
    @Override public boolean canHit(){return !isRemoved();}
    @Override public boolean isCollidable(){return true;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean shouldRender(double distance){return distance<2048.0*2048.0;}

    @Override public ActionResult interact(PlayerEntity player,Hand hand) {
        if(getWorld().isClient)return ActionResult.SUCCESS;
        if(!(player instanceof ServerPlayerEntity sp))return ActionResult.PASS;
        if(!permitted(player)){message(player,2);return ActionResult.CONSUME;}
        ItemStack stack=player.getStackInHand(hand);
        if(stack.isOf(Topol.MISSILE)) {
            if(state()!=STOWED || loaded()){message(player,11);return ActionResult.CONSUME;}
            dataTracker.set(LOADED,true);
            if(!player.getAbilities().creativeMode)stack.decrement(1);
            Packets.effect((ServerWorld)getWorld(),4,getPos(),getYaw(),getId());
            message(player,10);
        } else Packets.console(sp,getId(),0,0);
        return ActionResult.CONSUME;
    }
    @Override public boolean damage(DamageSource source,float amount) {
        if(getWorld().isClient)return false;
        if(source.getAttacker() instanceof PlayerEntity p) {
            if(p.isSneaking() && permitted(p) && state()==STOWED) {
                dropStack(new ItemStack(Topol.LAUNCHER_ITEM));
                if(loaded())dropStack(new ItemStack(Topol.MISSILE));
                discard(); return true;
            }
            return false;
        }
        if(amount>=12){discard();return true;}
        return false;
    }
    @Override public void tick() {
        super.tick();
        if(getWorld().isClient)return;
        int s=state(),t=stateTicks();
        ServerWorld world=(ServerWorld)getWorld();
        if(s==DEPLOYING && t>=DEPLOY_TICKS){setState(loaded()?READY:EMPTY);Packets.effect(world,4,getPos(),getYaw(),getId());}
        else if(s==STOWING && t>=STOW_TICKS){setState(STOWED);Packets.effect(world,4,getPos(),getYaw(),getId());}
        else if(s==LAUNCHING) {
            if(t>=20&&!capSent){capSent=true;Packets.effect(world,0,mouth(),getYaw(),getId());}
            if(t>=LAUNCH_TICKS) {
                if(FlightManager.launch(this)) {
                    dataTracker.set(LOADED,false);setState(EMPTY);
                    Packets.effect(world,1,mouth(),getYaw(),getId());
                } else setState(READY);
            }
        }
    }
    public void control(ServerPlayerEntity p,Packets.Control cmd) {
        long now=getWorld().getTime();
        if(p.squaredDistanceTo(getPos())>40*40){reply(p,7,0);return;}
        if(!permitted(p)){reply(p,2,0);return;}
        if(now-lastCommand<3)return;
        lastCommand=now;
        switch(cmd.action()) {
            case 0 -> {
                if(state()==STOWED) {
                    if(!clearAbove()){reply(p,12,0);return;}
                    disarm();setState(DEPLOYING);Packets.effect((ServerWorld)getWorld(),3,getPos(),getYaw(),getId());
                }
                reply(p,8,0);
            }
            case 1 -> {
                if(state()==READY||state()==EMPTY) {
                    disarm();setState(STOWING);Packets.effect((ServerWorld)getWorld(),3,getPos(),getYaw(),getId());
                }
                reply(p,8,0);
            }
            case 2 -> {
                if(state()!=READY||!loaded()){reply(p,3,0);return;}
                if(cmd.payload()>1){reply(p,4,0);return;}
                boolean destroy=cmd.payload()==1;
                if(destroy&&!Topol.CONFIG.enableDestruction){reply(p,14,0);return;}
                if(!validTarget(cmd.x(),cmd.z())){reply(p,4,0);return;}
                if(!clearAbove()){reply(p,12,0);return;}
                if(!FlightManager.hasCapacity()){reply(p,6,0);return;}
                dataTracker.set(TX,cmd.x());dataTracker.set(TZ,cmd.z());dataTracker.set(DESTRUCTIVE,destroy);
                token=UUID.randomUUID().getLeastSignificantBits();
                if(token==0)token=1;
                armedUntil=now+100;armedBy=p.getUuid();
                reply(p,1,token);
            }
            case 3 -> {
                if(token==0||cmd.token()!=token||now>armedUntil||!p.getUuid().equals(armedBy)){disarm();reply(p,5,0);return;}
                if(state()!=READY||!loaded()){disarm();reply(p,3,0);return;}
                if(cmd.x()!=targetX()||cmd.z()!=targetZ()||(cmd.payload()==1)!=destructive()){disarm();reply(p,5,0);return;}
                if(!clearAbove()){disarm();reply(p,12,0);return;}
                if(!FlightManager.hasCapacity()){disarm();reply(p,6,0);return;}
                disarm();setState(LAUNCHING);
                Packets.effect((ServerWorld)getWorld(),5,getPos(),getYaw(),getId());
                reply(p,8,0);
            }
            case 4 -> {disarm();reply(p,13,0);}
            default -> {}
        }
    }
    private boolean validTarget(int x,int z) {
        if(Math.abs((long)x)>29999000||Math.abs((long)z)>29999000)return false;
        double dx=x+.5-launchBase().x,dz=z+.5-launchBase().z,r=Math.hypot(dx,dz);
        return r>=Topol.CONFIG.minRange&&r<=Topol.CONFIG.maxRange&&getWorld().getWorldBorder().contains(new BlockPos(x,(int)getY(),z));
    }
    private boolean clearAbove() {
        if(getY()+27>=getWorld().getTopY())return false;
        BlockPos.Mutable b=new BlockPos.Mutable();
        // The entire swept canister volume, not just its eventual vertical axis.
        for(int x=-2;x<=2;x++)for(int z=-14;z<=12;z++)for(int y=5;y<=26;y++) {
            Vec3d p=local(x,y,z);b.set((int)Math.floor(p.x),(int)Math.floor(p.y),(int)Math.floor(p.z));
            if(!getWorld().getBlockState(b).getCollisionShape(getWorld(),b).isEmpty())return false;
        }
        return true;
    }
    private void disarm(){token=0;armedBy=null;armedUntil=0;}
    private void reply(ServerPlayerEntity p,int code,long token){Packets.console(p,getId(),code,token);}
    private static void message(PlayerEntity p,int code){p.sendMessage(Text.translatable("message.topol."+code),true);}
    @Override protected void writeCustomDataToNbt(NbtCompound n) {
        n.putInt("State",state());n.putLong("StateStart",dataTracker.get(START));n.putBoolean("Loaded",loaded());
        n.putInt("TargetX",targetX());n.putInt("TargetZ",targetZ());n.putBoolean("Destructive",destructive());
        n.putBoolean("CapSent",capSent);if(owner!=null)n.putUuid("Owner",owner);
    }
    @Override protected void readCustomDataFromNbt(NbtCompound n) {
        dataTracker.set(STATE,Math.max(0,Math.min(5,n.getInt("State"))));dataTracker.set(START,n.getLong("StateStart"));
        dataTracker.set(LOADED,n.getBoolean("Loaded"));dataTracker.set(TX,n.getInt("TargetX"));dataTracker.set(TZ,n.getInt("TargetZ"));
        dataTracker.set(DESTRUCTIVE,n.getBoolean("Destructive"));capSent=n.getBoolean("CapSent");
        if(n.containsUuid("Owner"))owner=n.getUuid("Owner");
        disarm();
    }
}
