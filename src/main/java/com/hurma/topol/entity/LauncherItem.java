package com.hurma.topol.entity;

import com.hurma.topol.Topol;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

public final class LauncherItem extends Item {
    public LauncherItem(Settings settings){super(settings);}
    @Override public ActionResult useOnBlock(ItemUsageContext c) {
        if(c.getWorld().isClient)return ActionResult.SUCCESS;
        if(c.getPlayer()==null||c.getSide()!=Direction.UP)return ActionResult.FAIL;
        ServerWorld w=(ServerWorld)c.getWorld();
        int count=0;
        for(Entity e:w.iterateEntities())if(e instanceof LauncherEntity&&!e.isRemoved())count++;
        if(count>=Topol.CONFIG.maxLaunchersPerWorld){c.getPlayer().sendMessage(Text.translatable("message.topol.15"),true);return ActionResult.FAIL;}
        BlockPos p=c.getBlockPos().up();
        LauncherEntity e=new LauncherEntity(Topol.LAUNCHER,w);
        e.setYaw(Math.round(c.getPlayerYaw()/90f)*90f);
        e.setPosition(p.getX()+.5,p.getY(),p.getZ()+.5);
        BlockPos.Mutable b=new BlockPos.Mutable();
        boolean clear=p.getY()+27<w.getTopY();
        for(int x=-2;x<=2&&clear;x++)for(int z=-14;z<=12&&clear;z++) {
            Vec3d v=e.local(x,0,z);
            int bx=(int)Math.floor(v.x),bz=(int)Math.floor(v.z);
            b.set(bx,p.getY()-1,bz);
            if(!w.getWorldBorder().contains(b)||!w.getBlockState(b).isSideSolidFullSquare(w,b,Direction.UP)){clear=false;break;}
            for(int y=0;y<6;y++) {
                b.set(bx,p.getY()+y,bz);
                if(!w.getBlockState(b).getCollisionShape(w,b).isEmpty()){clear=false;break;}
            }
        }
        if(!w.getOtherEntities(c.getPlayer(),e.getBoundingBox()).isEmpty())clear=false;
        if(!clear){c.getPlayer().sendMessage(Text.translatable("message.topol.9"),true);return ActionResult.FAIL;}
        e.setOwner(c.getPlayer().getUuid());
        if(!w.spawnEntity(e))return ActionResult.FAIL;
        if(!c.getPlayer().getAbilities().creativeMode)c.getStack().decrement(1);
        return ActionResult.CONSUME;
    }
}
