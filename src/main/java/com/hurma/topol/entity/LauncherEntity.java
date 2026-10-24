package com.hurma.topol.entity;
import com.hurma.topol.Topol;
import com.hurma.topol.net.Packets;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.UUID;

/** Compact single-round container. Reloadable game prop, not real machinery. */
public final class LauncherEntity extends Entity {
    private static final TrackedData<Boolean> LOADED=DataTracker.registerData(LauncherEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Long> FIRED=DataTracker.registerData(LauncherEntity.class,TrackedDataHandlerRegistry.LONG);
    private UUID owner;
    private boolean remoteIssued;
    public LauncherEntity(EntityType<? extends LauncherEntity> type,World world){super(type,world);setNoGravity(true);ignoreCameraFrustum=true;}
    @Override protected void initDataTracker(DataTracker.Builder b){b.add(LOADED,false);b.add(FIRED,-1000L);}
    public boolean loaded(){return dataTracker.get(LOADED);}
    public int sinceLaunch(){return (int)Math.min(10000,Math.max(0,getWorld().getTime()-dataTracker.get(FIRED)));}
    public void setOwner(UUID id){owner=id;}
    public boolean permitted(PlayerEntity p){return !Topol.CONFIG.ownerLock||owner==null||owner.equals(p.getUuid())||p.hasPermissionLevel(2);}
    public Vec3d mouth(){return getPos().add(0,3.6,0);}
    public Vec3d launchBase(){return getPos().add(0,.35,0);}
    public boolean clearAbove(){
        BlockPos.Mutable p=new BlockPos.Mutable();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=4;y<=16;y++){
            p.set((int)Math.floor(getX())+x,(int)Math.floor(getY())+y,(int)Math.floor(getZ())+z);
            if(p.getY()>=getWorld().getTopY()||!getWorld().getBlockState(p).getCollisionShape(getWorld(),p).isEmpty())return false;
        }
        return true;
    }
    public boolean consumeRound(PlayerEntity player){
        if(!loaded()||!permitted(player)||!clearAbove())return false;
        dataTracker.set(LOADED,false);dataTracker.set(FIRED,getWorld().getTime());return true;
    }
    @Override public boolean canHit(){return !isRemoved();}
    @Override public boolean isCollidable(){return true;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean shouldRender(double d){return d<1024*1024;}
    @Override public ActionResult interact(PlayerEntity p,Hand hand){
        if(getWorld().isClient)return ActionResult.SUCCESS;
        if(p.isSpectator()||!permitted(p)){message(p,"owner");return ActionResult.CONSUME;}
        ItemStack stack=p.getStackInHand(hand);
        if(stack.isOf(Topol.MISSILE)){
            if(loaded()||sinceLaunch()<30){message(p,"wait");return ActionResult.CONSUME;}
            dataTracker.set(LOADED,true);
            if(!p.getAbilities().creativeMode)stack.decrement(1);
            Packets.effect((ServerWorld)getWorld(),4,getPos(),0,getId());message(p,"loaded");
        }else if(stack.isOf(Topol.DESIGNATOR)){
            DesignatorItem.bind(stack,this);message(p,"bound");
        }else if(!remoteIssued){
            ItemStack remote=new ItemStack(Topol.DESIGNATOR);DesignatorItem.bind(remote,this);
            if(!p.giveItemStack(remote))dropStack(remote);
            remoteIssued=true;message(p,"bound");
        }else message(p,"hint");
        return ActionResult.CONSUME;
    }
    @Override public boolean damage(DamageSource source,float amount){
        if(getWorld().isClient)return false;
        if(source.getAttacker() instanceof PlayerEntity p){
            if(p.isSneaking()&&permitted(p)&&sinceLaunch()>30){
                dropStack(new ItemStack(Topol.LAUNCHER_ITEM));
                if(loaded())dropStack(new ItemStack(Topol.MISSILE));
                discard();return true;
            }
            return false;
        }
        if(amount>=12){discard();return true;}return false;
    }
    public static void message(PlayerEntity p,String key){p.sendMessage(Text.translatable("silo."+key),true);}
    @Override protected void writeCustomDataToNbt(NbtCompound n){
        n.putBoolean("Loaded",loaded());n.putLong("Fired",dataTracker.get(FIRED));n.putBoolean("RemoteIssued",remoteIssued);
        if(owner!=null)n.putUuid("Owner",owner);
    }
    @Override protected void readCustomDataFromNbt(NbtCompound n){
        dataTracker.set(LOADED,n.getBoolean("Loaded"));dataTracker.set(FIRED,n.getLong("Fired"));
        remoteIssued=n.getBoolean("RemoteIssued");if(n.containsUuid("Owner"))owner=n.getUuid("Owner");
    }
}
