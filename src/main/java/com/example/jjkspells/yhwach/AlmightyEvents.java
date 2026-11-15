package com.example.jjkspells.yhwach;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.yhwach.entity.AlmightyEntity;
import com.example.jjkspells.yhwach.spell.AlmightySpell;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The Almighty: caps single hits on the caster and lets the darkness devour whatever dies inside it. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AlmightyEvents {
    private AlmightyEvents() {
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || AlmightyEntity.ACTIVE.isEmpty()) {
            return;
        }
        for (AlmightyEntity a : AlmightyEntity.ACTIVE) {
            if (a.isOwner(victim)) {
                float cap = (float) (victim.getMaxHealth() * AlmightySpell.DAMAGE_CAP);
                if (event.getAmount() > cap) {
                    event.setAmount(cap);
                }
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || AlmightyEntity.ACTIVE.isEmpty()) {
            return;
        }
        for (AlmightyEntity a : AlmightyEntity.ACTIVE) {
            a.onDeathNearby(dead);
        }
    }
}
