package com.hurma.topol.entity;
import com.hurma.topol.Topol;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

public final class LauncherItem extends Item {
    public LauncherItem(Settings s){super(s);}
    @Override public ActionResult useOnBlock(ItemUsageContext c){
        if(c.getWorld().isClient)return ActionResult.SUCCESS;
        if(c.getPlayer()==null||c.getSide()!=Direction.UP)return ActionResult.FAIL;
        ServerWorld w=(ServerWorld)c.getWorld();
        int count=0;for(Entity e:w.iterateEntities())if(e instanceof LauncherEntity&&!e.isRemoved())count++;
        if(count>=Topol.CONFIG.maxLaunchersPerWorld){LauncherEntity.message(c.getPlayer(),"limit");return ActionResult.FAIL;}
        BlockPos p=c.getBlockPos().up();boolean clear=p.getY()+17<w.getTopY();
        BlockPos.Mutable b=new BlockPos.Mutable();
        for(int x=-1;x<=1&&clear;x++)for(int z=-1;z<=1&&clear;z++){
            b.set(p.getX()+x,p.getY()-1,p.getZ()+z);
            if(!w.getWorldBorder().contains(b)||!w.getBlockState(b).isSideSolidFullSquare(w,b,Direction.UP)){clear=false;break;}
            for(int y=0;y<5;y++){
                b.set(p.getX()+x,p.getY()+y,p.getZ()+z);
                if(!w.getBlockState(b).getCollisionShape(w,b).isEmpty()){clear=false;break;}
            }
        }
        LauncherEntity e=new LauncherEntity(Topol.LAUNCHER,w);e.setPosition(p.getX()+.5,p.getY(),p.getZ()+.5);
        if(!w.getOtherEntities(c.getPlayer(),e.getBoundingBox()).isEmpty())clear=false;
        if(!clear){LauncherEntity.message(c.getPlayer(),"space");return ActionResult.FAIL;}
        e.setOwner(c.getPlayer().getUuid());e.setYaw(Math.round(c.getPlayerYaw()/90)*90);
        if(!w.spawnEntity(e))return ActionResult.FAIL;
        if(!c.getPlayer().getAbilities().creativeMode)c.getStack().decrement(1);
        return ActionResult.CONSUME;
    }
}
