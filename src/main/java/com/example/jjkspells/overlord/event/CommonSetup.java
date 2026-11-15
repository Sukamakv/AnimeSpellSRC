package com.example.jjkspells.overlord.event;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.overlord.entity.BlackGoatEntity;
import com.example.jjkspells.overlord.entity.GoatSpawnEntity;
import com.example.jjkspells.overlord.entity.UndeadSoldierEntity;
import com.example.jjkspells.overlord.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CommonSetup {
    private CommonSetup() {
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(ModEntities.UNDEAD_SOLDIER.get(), UndeadSoldierEntity.createAttributes().build());
        e.put(ModEntities.BLACK_GOAT.get(), BlackGoatEntity.createAttributes().build());
        e.put(ModEntities.GOAT_SPAWN.get(), GoatSpawnEntity.createAttributes().build());
    }
}
