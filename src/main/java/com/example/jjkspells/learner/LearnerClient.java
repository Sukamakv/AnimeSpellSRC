package com.example.jjkspells.learner;

import com.example.jjkspells.JJKSpells;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LearnerClient {
    private LearnerClient() {
    }

    public static void receive(int containerId, java.util.List<LearnerEntries.Entry> entries) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof SkillLearnerMenu m && m.containerId == containerId) {
            m.entries.clear();
            m.entries.addAll(entries);
        }
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> MenuScreens.register(LearnerRegistry.MENU.get(), SkillLearnerScreen::new));
    }
}
