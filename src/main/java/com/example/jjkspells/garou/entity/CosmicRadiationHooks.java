package com.example.jjkspells.garou.entity;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.spell.GarouCosmicRadiationSpell;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** FORGE-bus hooks of Cosmic Radiation: the -20% healing and the registry of live controllers. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class CosmicRadiationHooks {
    /** Live server-side controllers. */
    public static final List<CosmicRadiationEntity> ACTIVE = new CopyOnWriteArrayList<>();

    private CosmicRadiationHooks() {}

    public static boolean hasAura(Entity caster) {
        for (CosmicRadiationEntity e : ACTIVE) {
            if (e.isOwnedBy(caster)) return true;
        }
        return false;
    }

    public static boolean isIrradiated(Entity target) {
        if (ACTIVE.isEmpty()) return false;
        for (CosmicRadiationEntity e : ACTIVE) {
            if (e.isIrradiated(target.getUUID())) return true;
        }
        return false;
    }

    /** Healing received is reduced by 20% while irradiated (regeneration, potions, spells... everything that calls heal()). */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity().level().isClientSide || ACTIVE.isEmpty()) return;
        if (isIrradiated(event.getEntity())) {
            event.setAmount(event.getAmount() * (1f - GarouCosmicRadiationSpell.HEAL_REDUCTION));
        }
    }
}
