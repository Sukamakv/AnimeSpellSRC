package com.example.jjkspells.yhwach;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.yhwach.entity.SanktZwingerEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Lets the sanctuary light up its walls when something inside it is attacked. Illumination only, damage is not changed. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SanktEvents {
    private SanktEvents() {
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide || SanktZwingerEntity.ACTIVE.isEmpty()) {
            return;
        }
        Entity victim = event.getEntity();
        for (SanktZwingerEntity s : SanktZwingerEntity.ACTIVE) {
            if (s.level() != victim.level() || !s.isInside(victim.position())) {
                continue;
            }
            DamageSource src = event.getSource();
            Vec3 from = src.getSourcePosition();
            if (from == null && src.getEntity() != null) {
                from = src.getEntity().position();
            }
            if (src.getDirectEntity() instanceof SanktZwingerEntity) {
                continue;
            }
            Vec3 at = from != null && !s.isInside(from) ? from : victim.position().add(0, 1.5, 0);
            Vec3 c = s.position();
            if (from != null && s.isInside(from)) {
                // attacker is inside as well: light the wall behind the victim
                Vec3 d = victim.position().subtract(c);
                at = c.add(d.x * 3, victim.getY() + 1.5 - c.y, d.z * 3);
            }
            s.ripple(at);
        }
    }
}
