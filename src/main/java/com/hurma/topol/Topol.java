package com.hurma.topol;

import com.hurma.topol.entity.LauncherEntity;
import com.hurma.topol.entity.LauncherItem;
import com.hurma.topol.entity.DesignatorItem;
import com.hurma.topol.net.Packets;
import com.hurma.topol.server.FlightManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.sound.*;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

public final class Topol implements ModInitializer {
    public static final String MODID="topol";
    public static final Logger LOG=LoggerFactory.getLogger(MODID);
    public static TopolConfig CONFIG=new TopolConfig();
    public static final Map<String,SoundEvent> SOUNDS=new LinkedHashMap<>();
    public static final String[] SOUND_NAMES={"clunk","cap","eject","ignition",
            "roar_near","roar_far","sonic_boom","blast_near","blast_far","ui_beep","ui_confirm","ui_error","earth"};
    public static EntityType<LauncherEntity> LAUNCHER;
    public static Item LAUNCHER_ITEM, MISSILE, DESIGNATOR;
    public static Block SCORCHED_EARTH;
    public static Identifier id(String path) {return Identifier.of(MODID,path);}
    public static SoundEvent sound(String name) {return SOUNDS.get(name);}

    @Override public void onInitialize() {
        CONFIG=TopolConfig.load();
        for(String name:SOUND_NAMES) SOUNDS.put(name,Registry.register(Registries.SOUND_EVENT,id(name),SoundEvent.of(id(name))));
        LAUNCHER=Registry.register(Registries.ENTITY_TYPE,id("launcher"),
                EntityType.Builder.<LauncherEntity>create(LauncherEntity::new,SpawnGroup.MISC)
                        .dimensions(2.1f,3.8f).maxTrackingRange(16).trackingTickInterval(1).build("topol:launcher"));
        LAUNCHER_ITEM=Registry.register(Registries.ITEM,id("launcher"),new LauncherItem(new Item.Settings().maxCount(1)));
        MISSILE=Registry.register(Registries.ITEM,id("missile"),new Item(new Item.Settings().maxCount(1)));
        DESIGNATOR=Registry.register(Registries.ITEM,id("designator"),new DesignatorItem(new Item.Settings().maxCount(1)));
        BlockSoundGroup earth=new BlockSoundGroup(1,1,sound("earth"),sound("earth"),sound("earth"),sound("earth"),sound("earth"));
        SCORCHED_EARTH=block("scorched_earth",new Block(AbstractBlock.Settings.create().strength(.7f,1).sounds(earth)));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e->{e.add(LAUNCHER_ITEM);e.add(MISSILE);e.add(DESIGNATOR);});
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(e->e.add(SCORCHED_EARTH));
        Packets.register();
        FlightManager.register();
        LOG.info("Topol game mod loaded; custom mesh/FX/audio, destruction={}",CONFIG.enableDestruction);
    }
    private static Block block(String name,Block block) {
        Registry.register(Registries.BLOCK,id(name),block);
        Registry.register(Registries.ITEM,id(name),new BlockItem(block,new Item.Settings()));
        return block;
    }
}
