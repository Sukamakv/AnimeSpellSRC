package com.example.jjkspells.malenia.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Malenia's Blade of Miquella, a netherite-grade sword. */
public final class ModItems {
    private static final DeferredRegister<Item> I = DeferredRegister.create(ForgeRegistries.ITEMS, JJKSpells.MODID);

    public static final RegistryObject<Item> BLADE = I.register("malenia_blade",
            () -> new SwordItem(Tiers.NETHERITE, 3, -2.2f, new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<Item> ROTTED_BLADE = I.register("rotted_blade", com.example.jjkspells.malenia.item.RottedBladeItem::new);

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        I.register(bus);
    }
}
