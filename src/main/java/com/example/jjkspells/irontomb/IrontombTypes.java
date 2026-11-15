package com.example.jjkspells.irontomb;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.irontomb.entity.IrontombEntity;
import com.example.jjkspells.irontomb.entity.IrontombSatellite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Registers the boss, its two satellites and a spawn egg. Everything hooks in through @EventBusSubscriber,
 * so no existing file of the mod has to be edited.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class IrontombTypes {
    // Built inside the register event: creating an EntityType in a static initialiser crashes with "Registry is already frozen".
    public static EntityType<IrontombEntity> IRONTOMB;
    public static EntityType<IrontombSatellite> SATELLITE;

    private static Item egg;
    private static Item configurator;

    private IrontombTypes() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent e) {
        e.register(ForgeRegistries.Keys.ENTITY_TYPES, h -> {
            IRONTOMB = EntityType.Builder.<IrontombEntity>of(IrontombEntity::new, MobCategory.MONSTER)
                    .sized(34.0f, 44.0f).clientTrackingRange(20).updateInterval(1).fireImmune()
                    .build(JJKSpells.id("irontomb").toString());
            SATELLITE = EntityType.Builder.<IrontombSatellite>of(IrontombSatellite::new, MobCategory.MONSTER)
                    .sized(6.0f, 6.0f).clientTrackingRange(20).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("irontomb_satellite").toString());
            h.register(JJKSpells.id("irontomb"), IRONTOMB);
            h.register(JJKSpells.id("irontomb_satellite"), SATELLITE);
        });
        e.register(ForgeRegistries.Keys.SOUND_EVENTS, h -> {
            for (var se : IrontombSounds.ALL) {
                h.register(se.getLocation(), se);
            }
        });
        e.register(ForgeRegistries.Keys.ITEMS, h -> {
            egg = new ForgeSpawnEggItem(() -> IRONTOMB, 0x15110f, 0xff3a1c, new Item.Properties()) {
                @Override
                public Component getName(ItemStack stack) {
                    return Component.literal("Irontomb Spawn Egg");
                }
            };
            h.register(JJKSpells.id("irontomb_spawn_egg"), egg);
            configurator = new IrontombConfiguratorItem();
            h.register(JJKSpells.id("irontomb_configurator"), configurator);
        });
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(IRONTOMB, IrontombEntity.createAttributes().build());
        e.put(SATELLITE, IrontombSatellite.createAttributes().build());
    }

    @SubscribeEvent
    public static void tabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS && egg != null) {
            e.accept(egg);
        }
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES && configurator != null) {
            e.accept(configurator);
        }
    }

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            IronsCompat.hook();
            IrontombNet.init();
        });
    }
}
