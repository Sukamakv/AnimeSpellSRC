package com.example.jjkspells.pantheon.event;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModCreativeTabs;
import com.example.jjkspells.pantheon.registry.ModItems;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Puts Pantheon's spear and shield into the mod's creative tab without touching ModCreativeTabs. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TabEvents {
    private TabEvents() {
    }

    @SubscribeEvent
    public static void tab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey().equals(ModCreativeTabs.MAIN.getKey())) {
            e.accept(ModItems.SPEAR.get());
            e.accept(ModItems.SHIELD.get());
        }
    }
}
