package com.example.jjkspells.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Viego inside his own Black Mist: +35% movement speed. The double melee damage is applied in ViegoEvents.
 * Harrowed Path refreshes this every few ticks while he stands in the mist, so it fades when he leaves.
 */
public class ViegoMistEffect extends MobEffect {
    public ViegoMistEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x2FBF7A);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, "7e1d0c94-3a52-4b6f-9d18-c4a35e2f8b01", 0.35, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
