package com.example.jjkspells.yasuo.entity;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.yasuo.spell.WindboundDuelSpell;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bonus damage Yasuo's own attacks deal to the enemy locked in a Windbound Duel. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class DuelEvents {
    /** dueled enemy -> Yasuo */
    public static final Map<UUID, UUID> DUELS = new ConcurrentHashMap<>();

    private DuelEvents() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        UUID owner = DUELS.get(event.getEntity().getUUID());
        if (owner == null) {
            return;
        }
        Entity src = event.getSource().getEntity();
        if (src != null && src.getUUID().equals(owner)) {
            event.setAmount((float) (event.getAmount() * (1.0 + WindboundDuelSpell.BONUS)));
        }
    }
}
