package com.example.jjkspells.effect;

import com.example.jjkspells.GarenConfig;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Courage: reduced damage (see GarenEvents) and knockback resistance while it lasts. */
public class CourageEffect extends MobEffect {
    public CourageEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xF4D77A);
        this.addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, "5b7f2d1a-3c44-4a77-9a10-6c0e1d2f8a31", 1.0, AttributeModifier.Operation.ADDITION);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        return GarenConfig.CO_KNOCKBACK_RESIST.get();
    }
}
