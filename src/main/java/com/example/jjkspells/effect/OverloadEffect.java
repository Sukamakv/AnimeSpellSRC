package com.example.jjkspells.effect;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Overload: the target cannot move, turn its camera, heal, cast spells, attack or use items, and has 15 less armor.
 * The behaviour itself lives in OverloadEvents (server) and OverloadClient (camera / input); this class carries
 * the attribute changes and freezes the AI of mobs.
 */
public class OverloadEffect extends MobEffect {
    private static final String NOAI_KEY = "jjk_overload_prev_noai";

    public OverloadEffect() {
        super(MobEffectCategory.HARMFUL, 0x3A2F9E);
        this.addAttributeModifier(Attributes.ARMOR, "7c1e2f6a-3d5b-4f08-9a41-b2e6d0c8a115", -15.0, AttributeModifier.Operation.ADDITION);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, "2a9f4c71-8e3d-4b56-a0c2-5d7e1f3b9a64", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        this.addAttributeModifier(Attributes.FLYING_SPEED, "e4b8d1a3-6c27-4e95-8f10-9a3c5b7d2e48", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public void addAttributeModifiers(LivingEntity entity, AttributeMap map, int amplifier) {
        super.addAttributeModifiers(entity, map, amplifier);
        if (entity instanceof Mob mob && !entity.level().isClientSide) {
            CompoundTag data = mob.getPersistentData();
            if (!data.contains(NOAI_KEY)) data.putBoolean(NOAI_KEY, mob.isNoAi());
            mob.setNoAi(true);
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap map, int amplifier) {
        super.removeAttributeModifiers(entity, map, amplifier);
        if (entity instanceof Mob mob && !entity.level().isClientSide) {
            CompoundTag data = mob.getPersistentData();
            if (data.contains(NOAI_KEY)) {
                mob.setNoAi(data.getBoolean(NOAI_KEY));
                data.remove(NOAI_KEY);
            }
        }
    }
}
