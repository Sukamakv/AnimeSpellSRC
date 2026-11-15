package com.example.jjkspells.effect;

import com.example.jjkspells.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Marker effect: while it is active the holder cannot be given Overload (see OverloadEvents)
 * and is immune to Malevolent Shrine's damage and effects (see ShrineManager).
 */
public class SimpleDomainEffect extends MobEffect {
    public SimpleDomainEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xF2D98A);
    }

    /** Keeps the effect on an entity for at least {@code ticks} more ticks (never shortens a longer one). */
    public static void grant(LivingEntity e, int ticks) {
        MobEffectInstance cur = e.getEffect(ModEffects.SIMPLE_DOMAIN.get());
        if (cur != null && cur.getDuration() >= ticks) return;
        e.addEffect(new MobEffectInstance(ModEffects.SIMPLE_DOMAIN.get(), ticks, 0, false, false, true));
    }

    public static boolean has(LivingEntity e) {
        return e != null && e.hasEffect(ModEffects.SIMPLE_DOMAIN.get());
    }
}
