package com.example.jjkspells.effect;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Bleeding: a wound that costs a little health every second. Applied with Irradiated so the target cannot heal. */
public class BleedingEffect extends MobEffect {
    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide || !entity.isAlive()) return;
        DamageSource src = entity.damageSources().generic();
        entity.invulnerableTime = 0;
        entity.hurt(src, 2.0f * (amplifier + 1));
    }
}
