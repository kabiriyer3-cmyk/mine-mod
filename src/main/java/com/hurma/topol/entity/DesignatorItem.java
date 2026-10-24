package com.hurma.topol.entity;

import com.hurma.topol.Topol;
import com.hurma.topol.server.FlightManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.world.World;
import java.util.UUID;

/** Native held-use item; clients cannot submit arbitrary target positions or launch commands. */
public final class DesignatorItem extends Item {
    public static final int USE_TICKS=72000;
    public DesignatorItem(Settings s){super(s);}
    public static void bind(ItemStack stack,LauncherEntity silo){
        NbtCompound n=new NbtCompound();n.putUuid("Silo",silo.getUuid());
        n.putString("Dimension",silo.getWorld().getRegistryKey().getValue().toString());
        stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));
    }
    public static UUID silo(ItemStack stack){
        NbtCompound n=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
        return n.containsUuid("Silo")?n.getUuid("Silo"):null;
    }
    public static String dimension(ItemStack stack){
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt().getString("Dimension");
    }
    @Override public int getMaxUseTime(ItemStack stack,LivingEntity user){return USE_TICKS;}
    @Override public UseAction getUseAction(ItemStack stack){return UseAction.BOW;}
    @Override public TypedActionResult<ItemStack> use(World w,PlayerEntity user,Hand hand){
        ItemStack stack=user.getStackInHand(hand);
        if(silo(stack)==null){if(!w.isClient)LauncherEntity.message(user,"unbound");return TypedActionResult.fail(stack);}
        if(!w.isClient&&user instanceof ServerPlayerEntity p){
            if(!FlightManager.beginAim(p,stack))return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);return TypedActionResult.consume(stack);
    }
    @Override public void usageTick(World w,LivingEntity user,ItemStack stack,int remaining){
        if(!w.isClient&&user instanceof ServerPlayerEntity p)FlightManager.aim(p,stack,USE_TICKS-remaining);
    }
    @Override public void onStoppedUsing(ItemStack stack,World w,LivingEntity user,int remaining){
        if(!w.isClient&&user instanceof ServerPlayerEntity p)FlightManager.endAim(p);
    }
}
