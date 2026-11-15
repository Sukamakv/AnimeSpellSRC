package com.example.jjkspells.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Sovereign's Domination's curse: the target's maximum health is cut by 40% while it lasts. */
public class SoulTornEffect extends MobEffect {
    public SoulTornEffect() {
        super(MobEffectCategory.HARMFUL, 0x6FE0A8);
        this.addAttributeModifier(Attributes.MAX_HEALTH, "e2b57c30-9a14-4d68-b3f1-5c0a8d7e4926", -0.40, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** Health above the new maximum is lost straight away (the soul is gone, the body keeps only what fits). */
    @Override
    public void addAttributeModifiers(LivingEntity entity, AttributeMap map, int amplifier) {
        super.addAttributeModifiers(entity, map, amplifier);
        if (entity.getHealth() > entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
    }
}
