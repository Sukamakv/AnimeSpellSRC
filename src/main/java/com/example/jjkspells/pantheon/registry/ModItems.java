package com.example.jjkspells.pantheon.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Pantheon's spear (a netherite-grade weapon) and his shield (a keepsake item to hold in the off hand). */
public final class ModItems {
    private static final DeferredRegister<Item> I = DeferredRegister.create(ForgeRegistries.ITEMS, JJKSpells.MODID);

    public static final RegistryObject<Item> SPEAR = I.register("pantheon_spear",
            () -> new SwordItem(Tiers.NETHERITE, 3, -2.4f, new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> SHIELD = I.register("pantheon_shield",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(1)));

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        I.register(bus);
    }
}
