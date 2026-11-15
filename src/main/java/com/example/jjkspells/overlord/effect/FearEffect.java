package com.example.jjkspells.overlord.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Marker effect: frightened entities cannot attack, target or cast (see OverlordEvents). */
public class FearEffect extends MobEffect {
    public FearEffect() {
        super(MobEffectCategory.HARMFUL, 0x3A1A55);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(net.minecraft.world.entity.LivingEntity e, int amplifier) {
        if (e.level().isClientSide) {
            return;
        }
        if (e instanceof net.minecraft.world.entity.Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.setAggressive(false);
        }
    }
}
