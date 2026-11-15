package com.example.jjkspells.acheron.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Nihility's Bane, Acheron's katana. */
public final class ModItems {
    private static final DeferredRegister<Item> I = DeferredRegister.create(ForgeRegistries.ITEMS, JJKSpells.MODID);

    public static final RegistryObject<Item> NIHILITYS_BANE = I.register("nihilitys_bane",
            com.example.jjkspells.acheron.item.NihilitysBaneItem::new);

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        I.register(bus);
    }
}
