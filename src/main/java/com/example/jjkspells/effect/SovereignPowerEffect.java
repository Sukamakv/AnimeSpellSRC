package com.example.jjkspells.effect;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.UUID;

/** Viego after possessing a soul: +50% spell power. The modifier is added when the effect starts (Iron's registers late). */
public class SovereignPowerEffect extends MobEffect {
    private static final UUID SPELL_POWER_ID = UUID.fromString("0f6c93b8-27d4-4a1e-b5e9-8a31c7d2f650");

    public SovereignPowerEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x7CFFC4);
    }

    @Override
    public void addAttributeModifiers(LivingEntity entity, AttributeMap map, int amplifier) {
        super.addAttributeModifiers(entity, map, amplifier);
        AttributeInstance power = map.getInstance(AttributeRegistry.SPELL_POWER.get());
        if (power != null) {
            power.removeModifier(SPELL_POWER_ID);
            power.addTransientModifier(new AttributeModifier(SPELL_POWER_ID, "Sovereign's Domination", 0.50, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap map, int amplifier) {
        super.removeAttributeModifiers(entity, map, amplifier);
        AttributeInstance power = map.getInstance(AttributeRegistry.SPELL_POWER.get());
        if (power != null) power.removeModifier(SPELL_POWER_ID);
    }
}
