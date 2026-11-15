package com.example.jjkspells.overlord.effect;

import com.example.jjkspells.overlord.config.OverlordConfig;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Removes a configurable fraction (default 50%) of max health for the duration. */
public class DeathCurseEffect extends MobEffect {
    public DeathCurseEffect() {
        super(MobEffectCategory.HARMFUL, 0x1C0A24);
        this.addAttributeModifier(Attributes.MAX_HEALTH, "5b2c9c1e-7a0e-4d57-a1f6-0c0de0000001", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        return -OverlordConfig.GOAL.maxHealthLoss.get();
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity e, int amplifier) {
        if (!e.level().isClientSide && e.getHealth() > e.getMaxHealth()) {
            e.setHealth(e.getMaxHealth());
        }
    }
}
