package com.example.jjkspells.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * World Ender: Aatrox's true Darkin form. +20% movement speed here; +50% damage, lifesteal, bigger sword attacks and
 * the wings and aura are handled in AatroxEvents and the spells. The amplifier is the spell level - 1.
 */
public class WorldEnderEffect extends MobEffect {
    public WorldEnderEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xA01018);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, "9b3f6d12-c7a4-4e58-8f01-52d8a64e7b39", 0.20, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
