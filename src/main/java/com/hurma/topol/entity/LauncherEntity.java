package com.hurma.topol.entity;
import com.hurma.topol.Topol;
import com.hurma.topol.net.Packets;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.UUID;

/**
 * Embedded single-round silo. The entity sits on the ground surface; its 1x7 shaft column is carved on
 * placement (original blocks remembered and put back when the silo is packed up). Game prop, not real machinery.
 */
public final class LauncherEntity extends Entity {
    private static final TrackedData<Boolean> LOADED=DataTracker.registerData(LauncherEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Long> FIRED=DataTracker.registerData(LauncherEntity.class,TrackedDataHandlerRegistry.LONG);
    public static final int SHAFT=7;
    /** Ticks after lock: hatch opens 2..18, booster lights at 24, round leaves at 30, hatch closes 100..124. */
    public static final int LAUNCH_DELAY=30, IGNITION=24, CLOSE_START=100, CYCLE=126;
    private UUID owner;
    private boolean remoteIssued;
    private final String[] shaft=new String[SHAFT];
    public LauncherEntity(EntityType<? extends LauncherEntity> type,World world){super(type,world);setNoGravity(true);ignoreCameraFrustum=true;}
    @Override protected void initDataTracker(DataTracker.Builder b){b.add(LOADED,false);b.add(FIRED,-1000L);}
    public boolean loaded(){return dataTracker.get(LOADED);}
    public int sinceLaunch(){return (int)Math.min(10000,Math.max(0,getWorld().getTime()-dataTracker.get(FIRED)));}
    public static float hatch(float t,boolean loaded){
        if(loaded&&t>CYCLE)return 0;
        float open=ease((t-2)/16f),close=ease((t-CLOSE_START)/24f);
        return Math.max(0,open-close);
    }
    private static float ease(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    public void setOwner(UUID id){owner=id;}
    public boolean permitted(PlayerEntity p){return !Topol.CONFIG.ownerLock||owner==null||owner.equals(p.getUuid())||p.hasPermissionLevel(2);}
    public Vec3d mouth(){return getPos().add(0,.3,0);}
    public Vec3d launchBase(){return getPos().add(0,-6.6,0);}

    /** Placement helper: true when the 1x7 column under the pad can be opened. */
    public static boolean shaftDiggable(World w,BlockPos top){
        BlockPos.Mutable p=new BlockPos.Mutable();
        for(int i=1;i<=SHAFT;i++){
            p.set(top.getX(),top.getY()-i,top.getZ());
            if(p.getY()<=w.getBottomY())return false;
            BlockState s=w.getBlockState(p);
            if(w.getBlockEntity(p)!=null||s.getHardness(w,p)<0||!s.getFluidState().isEmpty())return false;
        }
        return true;
    }
    public void digShaft(){
        BlockPos top=getBlockPos();BlockPos.Mutable p=new BlockPos.Mutable();
        for(int i=1;i<=SHAFT;i++){
            p.set(top.getX(),top.getY()-i,top.getZ());
            BlockState s=getWorld().getBlockState(p);
            shaft[i-1]=s.isAir()?null:Registries.BLOCK.getId(s.getBlock()).toString();
            getWorld().setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS|Block.SKIP_DROPS);
        }
    }
    private void restoreShaft(){
        BlockPos top=getBlockPos();BlockPos.Mutable p=new BlockPos.Mutable();
        for(int i=1;i<=SHAFT;i++){
            if(shaft[i-1]==null)continue;
            p.set(top.getX(),top.getY()-i,top.getZ());
            if(!getWorld().getBlockState(p).isAir())continue;
            Identifier id=Identifier.tryParse(shaft[i-1]);
            Block block=id==null?Blocks.DIRT:Registries.BLOCK.get(id);
            getWorld().setBlockState(p,(block==Blocks.AIR?Blocks.DIRT:block).getDefaultState(),Block.NOTIFY_ALL);
        }
    }
    public boolean clearAbove(){
        BlockPos.Mutable p=new BlockPos.Mutable();
        int bx=(int)Math.floor(getX()),by=(int)Math.floor(getY()),bz=(int)Math.floor(getZ());
        for(int y=-SHAFT;y<=18;y++){
            p.set(bx,by+y,bz);
            if(p.getY()>=getWorld().getTopY())break;
            if(!getWorld().getBlockState(p).getCollisionShape(getWorld(),p).isEmpty())return false;
        }
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<=2;y++){
            p.set(bx+x,by+y,bz+z);
            if(!getWorld().getBlockState(p).getCollisionShape(getWorld(),p).isEmpty())return false;
        }
        return true;
    }
    public boolean consumeRound(PlayerEntity player){
        if(!loaded()||!permitted(player)||!clearAbove())return false;
        dataTracker.set(LOADED,false);dataTracker.set(FIRED,getWorld().getTime());return true;
    }
    @Override public void tick(){
        super.tick();
        if(!getWorld().isClient&&getWorld().getTime()-dataTracker.get(FIRED)==CLOSE_START)
            Packets.effect((ServerWorld)getWorld(),6,mouth(),getYaw(),getId());
    }
    @Override public boolean canHit(){return !isRemoved();}
    @Override public boolean isCollidable(){return true;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean shouldRender(double d){return d<512*512;}
    @Override public ActionResult interact(PlayerEntity p,Hand hand){
        if(getWorld().isClient)return ActionResult.SUCCESS;
        if(p.isSpectator()||!permitted(p)){message(p,"owner");return ActionResult.CONSUME;}
        ItemStack stack=p.getStackInHand(hand);
        if(stack.isOf(Topol.MISSILE)){
            if(loaded()||sinceLaunch()<CYCLE){message(p,"wait");return ActionResult.CONSUME;}
            dataTracker.set(LOADED,true);
            if(!p.getAbilities().creativeMode)stack.decrement(1);
            Packets.effect((ServerWorld)getWorld(),4,mouth(),0,getId());message(p,"loaded");
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
            if(p.isSneaking()&&permitted(p)&&sinceLaunch()>=CYCLE){
                dropStack(new ItemStack(Topol.LAUNCHER_ITEM));
                if(loaded())dropStack(new ItemStack(Topol.MISSILE));
                restoreShaft();discard();return true;
            }
            return false;
        }
        if(amount>=12){discard();return true;}return false;
    }
    public static void message(PlayerEntity p,String key){p.sendMessage(Text.translatable("silo."+key),true);}
    @Override protected void writeCustomDataToNbt(NbtCompound n){
        n.putBoolean("Loaded",loaded());n.putLong("Fired",dataTracker.get(FIRED));n.putBoolean("RemoteIssued",remoteIssued);
        if(owner!=null)n.putUuid("Owner",owner);
        for(int i=0;i<SHAFT;i++)if(shaft[i]!=null)n.putString("Shaft"+i,shaft[i]);
    }
    @Override protected void readCustomDataFromNbt(NbtCompound n){
        dataTracker.set(LOADED,n.getBoolean("Loaded"));dataTracker.set(FIRED,n.getLong("Fired"));
        remoteIssued=n.getBoolean("RemoteIssued");if(n.containsUuid("Owner"))owner=n.getUuid("Owner");
        for(int i=0;i<SHAFT;i++){String s=n.getString("Shaft"+i);shaft[i]=s.isEmpty()||s.length()>96?null:s;}
    }
}
