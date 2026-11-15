package com.example.jjkspells.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** The price of Hollow Purple: Nine Ropes. -10% maximum health. */
public class BacklashEffect extends MobEffect {
    public BacklashEffect() {
        super(MobEffectCategory.HARMFUL, 0x8A1B3D);
        this.addAttributeModifier(Attributes.MAX_HEALTH, "3f6b1c9e-2a74-4d85-b0e1-7c5a9d2f4b36", -0.10, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
