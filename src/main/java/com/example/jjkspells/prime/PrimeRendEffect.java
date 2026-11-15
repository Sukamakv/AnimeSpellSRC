package com.example.jjkspells.prime;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Heavenly Onslaught's wound: -15% maximum health (the exact fraction is the config's, applied by the amplifier-free base value). */
public class PrimeRendEffect extends MobEffect {
    public PrimeRendEffect() {
        super(MobEffectCategory.HARMFUL, 0xFF3A6A);
        addAttributeModifier(Attributes.MAX_HEALTH, "e2d4a6c8-1f3b-4a5c-9e7d-6b8a0c2e4f13", -0.15, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity e, int amplifier) {
        if (e.getHealth() > e.getMaxHealth()) e.setHealth(e.getMaxHealth());
    }
}
