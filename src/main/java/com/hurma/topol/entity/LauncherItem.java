package com.hurma.topol.entity;
import com.hurma.topol.Topol;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Places the silo flush with the ground: 3x3 solid footing, open sky lane, diggable 1x7 shaft. */
public final class LauncherItem extends Item {
    public LauncherItem(Settings s){super(s);}
    @Override public ActionResult useOnBlock(ItemUsageContext c){
        if(c.getWorld().isClient)return ActionResult.SUCCESS;
        if(c.getPlayer()==null||c.getSide()!=Direction.UP)return ActionResult.FAIL;
        ServerWorld w=(ServerWorld)c.getWorld();
        int count=0;for(Entity e:w.iterateEntities())if(e instanceof LauncherEntity&&!e.isRemoved())count++;
        if(count>=Topol.CONFIG.maxLaunchersPerWorld){LauncherEntity.message(c.getPlayer(),"limit");return ActionResult.FAIL;}
        BlockPos p=c.getBlockPos().up();boolean clear=p.getY()+20<w.getTopY();
        BlockPos.Mutable b=new BlockPos.Mutable();
        for(int x=-1;x<=1&&clear;x++)for(int z=-1;z<=1&&clear;z++){
            b.set(p.getX()+x,p.getY()-1,p.getZ()+z);
            if(!w.getWorldBorder().contains(b)||!w.getBlockState(b).isSideSolidFullSquare(w,b,Direction.UP)){clear=false;break;}
            for(int y=0;y<3;y++){
                b.set(p.getX()+x,p.getY()+y,p.getZ()+z);
                if(!w.getBlockState(b).getCollisionShape(w,b).isEmpty()){clear=false;break;}
            }
        }
        for(int y=3;y<18&&clear;y++){
            b.set(p.getX(),p.getY()+y,p.getZ());
            if(!w.getBlockState(b).getCollisionShape(w,b).isEmpty())clear=false;
        }
        if(clear&&!LauncherEntity.shaftDiggable(w,p))clear=false;
        LauncherEntity e=new LauncherEntity(Topol.LAUNCHER,w);e.setPosition(p.getX()+.5,p.getY(),p.getZ()+.5);
        if(clear&&!w.getOtherEntities(null,e.getBoundingBox().expand(0,.5,0)).isEmpty())clear=false;
        if(!clear){LauncherEntity.message(c.getPlayer(),"space");return ActionResult.FAIL;}
        e.setOwner(c.getPlayer().getUuid());e.setYaw(Math.round(c.getPlayerYaw()/90)*90);
        if(!w.spawnEntity(e))return ActionResult.FAIL;
        e.digShaft();
        com.hurma.topol.net.Packets.effect(w,4,e.mouth(),0,e.getId());
        if(!c.getPlayer().getAbilities().creativeMode)c.getStack().decrement(1);
        return ActionResult.CONSUME;
    }
}
