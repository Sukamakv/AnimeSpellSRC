package com.example.jjkspells.irontomb;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.irontomb.entity.PhainonEntity;
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
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** Registers Phainon, his spawn egg, the Dawnbreaker's Seal and his sounds. Hooks in through @EventBusSubscriber. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PhainonTypes {
    // Built inside the register event: creating an EntityType in a static initialiser crashes with "Registry is already frozen".
    public static EntityType<PhainonEntity> PHAINON;

    private static Item egg;
    private static Item seal;

    private PhainonTypes() {
    }

    public static Item seal() {
        return seal;
    }

    @SubscribeEvent
    public static void register(RegisterEvent e) {
        e.register(ForgeRegistries.Keys.ENTITY_TYPES, h -> {
            PHAINON = EntityType.Builder.<PhainonEntity>of(PhainonEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.3f).clientTrackingRange(16).updateInterval(1).fireImmune()
                    .build(JJKSpells.id("phainon").toString());
            h.register(JJKSpells.id("phainon"), PHAINON);
        });
        e.register(ForgeRegistries.Keys.SOUND_EVENTS, h -> {
            for (var se : PhainonSounds.ALL) {
                h.register(se.getLocation(), se);
            }
        });
        e.register(ForgeRegistries.Keys.ITEMS, h -> {
            egg = new ForgeSpawnEggItem(() -> PHAINON, 0x2a1850, 0xffc83c, new Item.Properties()) {
                @Override
                public Component getName(ItemStack stack) {
                    return Component.literal("Phainon Spawn Egg");
                }
            };
            h.register(JJKSpells.id("phainon_spawn_egg"), egg);
            seal = new PhainonSealItem();
            h.register(JJKSpells.id("phainon_seal"), seal);
        });
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(PHAINON, PhainonEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void tabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS && egg != null) {
            e.accept(egg);
        }
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES && seal != null) {
            e.accept(seal);
        }
    }
}
