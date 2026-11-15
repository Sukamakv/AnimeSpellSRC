package com.example.jjkspells.garou;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.entity.GammaBeamEntity;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Releases every locked caster and removes every temporary light block when the world is shut down. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class GarEvents {
    private GarEvents() {
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        GammaBeamEntity.cleanupAll();
    }
}
