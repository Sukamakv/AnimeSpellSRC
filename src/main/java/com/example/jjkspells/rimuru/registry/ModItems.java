package com.example.jjkspells.rimuru.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Rimuru's slime blade, a netherite-grade sword. */
public final class ModItems {
    private static final DeferredRegister<Item> I = DeferredRegister.create(ForgeRegistries.ITEMS, JJKSpells.MODID);

    public static final RegistryObject<Item> BLADE = I.register("rimuru_blade",
            () -> new SwordItem(Tiers.NETHERITE, 3, -2.2f, new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        I.register(bus);
    }
}
