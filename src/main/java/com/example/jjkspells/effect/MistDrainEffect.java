package com.example.jjkspells.effect;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * An enemy inside Viego's Black Mist: 20% slower and 35% less spell power. Weakness II is applied separately as the
 * normal vanilla effect. Harrowed Path refreshes this every few ticks while the enemy is in the mist.
 *
 * Spell power belongs to Iron's, which registers after vanilla effects, so that modifier is added when the effect
 * starts (not when this class is built).
 */
public class MistDrainEffect extends MobEffect {
    private static final UUID SPELL_POWER_ID = UUID.fromString("b9a4e2d1-6c03-4f7a-8e55-0d1f3a7c9e42");

    public MistDrainEffect() {
        super(MobEffectCategory.HARMFUL, 0x1B4D3E);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, "4c8f2a61-d3b7-4e90-a1c5-72be6f0d3a18", -0.20, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public void addAttributeModifiers(LivingEntity entity, AttributeMap map, int amplifier) {
        super.addAttributeModifiers(entity, map, amplifier);
        AttributeInstance power = map.getInstance(AttributeRegistry.SPELL_POWER.get());
        if (power != null) {
            power.removeModifier(SPELL_POWER_ID);
            power.addTransientModifier(new AttributeModifier(SPELL_POWER_ID, "Black Mist", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap map, int amplifier) {
        super.removeAttributeModifiers(entity, map, amplifier);
        AttributeInstance power = map.getInstance(AttributeRegistry.SPELL_POWER.get());
        if (power != null) power.removeModifier(SPELL_POWER_ID);
    }
}
