package com.example.jjkspells.rimuru.event;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModCreativeTabs;
import com.example.jjkspells.rimuru.registry.ModItems;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Puts Rimuru's blade into the mod's creative tab without touching ModCreativeTabs. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TabEvents {
    private TabEvents() {
    }

    @SubscribeEvent
    public static void tab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey().equals(ModCreativeTabs.MAIN.getKey())) {
            e.accept(ModItems.BLADE.get());
        }
    }
}
