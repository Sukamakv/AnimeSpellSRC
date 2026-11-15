package com.example.jjkspells.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Umbral Dash's braced stance: knockback resistance while he surges, so he cannot be shoved off his line. */
public class UmbralStanceEffect extends MobEffect {
    public UmbralStanceEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x5A0A10);
        this.addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, "1c7e2a95-d034-4b6f-9a18-e3f5b0c82d47", 1.0, AttributeModifier.Operation.ADDITION);
    }
}
